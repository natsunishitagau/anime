import os
from langchain.chat_models import init_chat_model

api_key=os.environ.get("DASHSCOPE_API_KEY")

llm = init_chat_model(
    api_key=api_key,
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    model="openai:qwen3.6-flash"
)

if __name__ == "__main__":
    from langchain.agents import create_agent
    from langchain.tools import tool
    from langchain.messages import HumanMessage
    @tool
    def squre_root(x: float) -> float:
        """
        Calculate the square root of a number.
        """
        return x**0.5

    agent = create_agent(
        model=llm,
    )
    for token, data in agent.stream(
                        {"messages": [HumanMessage(content="100000086的平方根是多少？调用平方根工具!")]},
                        tools=[squre_root],
                        stream_mode="messages",
    ):
        print(token)
