from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from typing import List, Optional
from langchain_core.messages import HumanMessage, AIMessage
from app.agent.anime_master import chat_stream_with_context, get_conversation_history, delete_conversation_history
from app.db.user_chats import get_user_chats, create_user_chat, update_chat_updated_at, delete_user_chat
from app.utils.snowflake import generate_snowflake_id
from app.models.chat import llm
import json

router = APIRouter()

class ChatRequest(BaseModel):
    message: str
    thread_id: str

class NewChatRequest(BaseModel):
    user_id: int
    message: str
    thread_id: Optional[str] = None

class GenerateThreadIdResponse(BaseModel):
    thread_id: str

class ChatMessage(BaseModel):
    role: str
    content: str

class HistoryResponse(BaseModel):
    thread_id: str
    messages: List[ChatMessage]

class ThreadInfo(BaseModel):
    thread_id: str
    title: str
    updated_at: Optional[str]

class NewChatResponse(BaseModel):
    thread_id: str
    title: str

def summarize_message(message: str) -> str:
    """
    使用LLM总结消息内容作为对话标题
    :param message: 用户消息
    :return: 总结标题
    """
    try:
        prompt = f"""
        请将以下用户提问总结成一个简洁的中文标题，不超过20个字：
        {message}
        """
        response = llm.invoke(prompt)
        title = response.content.strip()
        # 如果总结太长，截取前20个字符
        if len(title) > 20:
            title = title[:20]
        return title
    except Exception as e:
        print(f"[ChatRouter] 总结消息失败: {e}")
        # 如果总结失败，使用消息前20个字符作为标题
        return message[:20]

@router.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """
    流式聊天接口
    """
    async def generate():
        try:
            async for event in chat_stream_with_context(request.message, request.thread_id):
                data = {
                    "type": event.get("type"),
                    "content": event.get("content"),
                }

                yield f"data: {json.dumps(data, ensure_ascii=False)}\n\n"

            yield "data: {\"type\": \"done\"}\n\n"

            # 更新关联表的updated_at
            update_chat_updated_at(request.thread_id)

        except Exception as e:
            error_data = {
                "type": "error",
                "content": str(e)
            }
            yield f"data: {json.dumps(error_data, ensure_ascii=False)}\n\n"
    
    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )

@router.get("/chat/generate-thread-id", response_model=GenerateThreadIdResponse)
async def generate_thread_id():
    """
    生成新的thread_id接口
    用于前端预先获取thread_id，降低新建对话的延迟感
    """
    try:
        thread_id = generate_snowflake_id()
        return GenerateThreadIdResponse(thread_id=thread_id)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/chat/new", response_model=NewChatResponse)
async def new_chat(request: NewChatRequest):
    """
    新建用户对话接口
    支持接收前端传入的thread_id，或自动生成
    使用LLM总结第一次提问作为标题
    """
    try:
        # 使用传入的thread_id或自动生成
        thread_id = request.thread_id if request.thread_id else generate_snowflake_id()
        
        # 使用LLM总结第一次提问作为标题
        title = summarize_message(request.message)
        
        # 创建用户对话关联记录
        success = create_user_chat(request.user_id, thread_id, title)
        if not success:
            raise HTTPException(status_code=500, detail="创建对话失败")
        
        return NewChatResponse(
            thread_id=thread_id,
            title=title
        )
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/chat/threads/{user_id}", response_model=List[ThreadInfo])
async def get_user_threads(user_id: int):
    """
    获取指定用户的对话列表
    """
    try:
        conversations = get_user_chats(user_id)
        return conversations
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/history/{thread_id}", response_model=HistoryResponse)
async def get_history(thread_id: str):
    """
    获取指定thread_id的历史对话
    """
    try:
        current_messages = await get_conversation_history(thread_id)
        
        if not current_messages:
            raise HTTPException(status_code=404, detail="Thread not found")
        
        messages = []
        for msg in current_messages:
            if msg.content=='':
                continue
            if isinstance(msg, HumanMessage):
                messages.append(ChatMessage(role="user", content=msg.content))
            elif isinstance(msg, AIMessage):
                messages.append(ChatMessage(role="assistant", content=msg.content))
        
        return HistoryResponse(
            thread_id=thread_id,
            messages=messages
        )
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.delete("/history/{thread_id}")
async def delete_history(thread_id: str):
    """
    删除指定thread_id的对话记录
    """
    try:
        # 删除用户对话关联记录
        delete_user_chat(thread_id)
        
        # 删除实际对话记录（即使checkpointer中没有状态也不影响）
        await delete_conversation_history(thread_id)
        
        return {"message": f"History for thread {thread_id} deleted successfully"}
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))