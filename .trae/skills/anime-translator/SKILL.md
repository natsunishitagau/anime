---
name: "anime-translator"
description: "读取数据库anime_db的anime实体，将指定属性翻译为中文后写入数据库。使用MySQL命令行直接操作，支持批量处理，调用baoyu-translate技能进行翻译。"
version: "2.1.0"
author: "trae"
created_at: "2026-05-19"
tags: ["database", "translation", "anime", "mysql"]

input:
  - name: "id"
    type: "number"
    required: false
    description: "要翻译的anime实体ID，不指定则读取全部实体或批量处理"

  - name: "fields"
    type: "array"
    required: false
    default: ["synopsis"]
    description: "需要翻译的字段列表，可选值: title, synopsis, type, season, status, studios, genres, source"

  - name: "batch_size"
    type: "number"
    required: false
    default: 10
    description: "批量处理时每次处理的实体个数，默认10个"

  - name: "start_id"
    type: "number"
    required: false
    description: "批量处理时开始的ID，从此ID开始往后读取batch_size个实体"

  - name: "translate_mode"
    type: "string"
    required: false
    default: "refined"
    description: "翻译模式：quick(快速), normal(标准), refined(精细)，默认使用精细翻译"

output:
  - name: "translated_ids"
    type: "array"
    description: "已翻译的anime实体ID列表"

  - name: "count"
    type: "number"
    description: "成功翻译并保存的实体数量"

  - name: "batch_info"
    type: "object"
    description: "批量处理信息，包含start_id, end_id, batch_size"

  - name: "output_dir"
    type: "string"
    description: "临时输出目录路径"

usage:
  - scenario: "翻译单个anime实体"
    input:
      id: 1
      fields: ["synopsis"]
    output:
      translated_ids: [1]
      count: 1
      batch_info: null
      output_dir: "d:/temp/anime_translate/"

  - scenario: "批量翻译，从ID 15开始处理10个实体"
    input:
      batch_size: 10
      start_id: 15
      fields: ["synopsis"]
    output:
      translated_ids: [15, 21, 24, 28, 30, 31, ...]
      count: 10
      batch_info:
        start_id: 15
        end_id: 24
        batch_size: 10
      output_dir: "d:/temp/anime_translate/"

features:
  - "使用MySQL命令行直接读取anime_db数据库"
  - "创建临时目录存储中间文件"
  - "批量读取指定范围的anime实体"
  - "自动调用baoyu-translate技能进行英中翻译"
  - "使用精细翻译模式（refined）"
  - "支持文本分块处理（大内容自动拆分）"
  - "清理翻译结果中的乱码字符"
  - "移除翻译结果中的来源标记（如 [Written by MAL Rewrite]）"
  - "生成UPDATE SQL脚本"
  - "执行SQL更新数据库"
  - "无中文对应语义时保留原字段"
  - "确保每个字段都经过翻译验证"

---

## 技能说明

该技能使用MySQL命令行直接读取anime数据库中的anime实体，自动调用baoyu-translate技能将指定的文本字段翻译为中文，并将翻译结果保存回数据库。

### 功能流程

1. **创建临时目录**: 在 `d:/temp/anime_translate/` 创建临时目录用于存储中间文件
2. **读取实体**: 使用MySQL命令读取指定ID或批量ID范围的anime实体
3. **准备翻译内容**: 将原始数据写入 `anime_synopsis.md`
4. **分块处理**: 调用 `baoyu-translate` 脚本进行内容分块（大内容自动拆分）
5. **精细翻译处理**: 使用baoyu-translate的**精细翻译模式（refined）**进行翻译：
6. **清理处理**: 清理翻译结果中的乱码字符和来源标记（如 [Written by MAL Rewrite]）
7. **生成更新SQL**: 将翻译结果生成为UPDATE SQL语句
8. **执行更新**: 使用mysql -u root -pCptbtptp123! -h localhost -P 3306 --default-character-set=utf8mb4 anime_db -e "source d:/temp/anime_translate/update.sql"执行更新
9. **返回结果**: 返回翻译后的实体ID列表和处理信息

### 清理处理规则

翻译完成后，系统会自动清理以下内容：

1. **乱码字符**: 移除翻译过程中可能产生的不可见字符、特殊符号和编码错误字符
2. **来源标记**: 移除 `[Written by MAL Rewrite]`、`[译自 MAL Rewrite]` 等来源标记
3. **多余空白**: 清理多余的换行符和空格

### 批量处理规则

- `start_id`: 指定开始ID，系统会从该ID开始往上读取 `batch_size` 个实体
- `batch_size`: 每次处理的实体个数（默认10个）
- 处理顺序：按ID升序处理

### 临时文件结构

```
d:/temp/anime_translate/
├── anime_synopsis.md      # 原始英文内容
├── translation.md         # 中文翻译结果
├── update.sql            # 更新SQL脚本
└── chunks/
    └── chunk-01.md       # 分块后的内容（大文件时会有多个）
```

### 支持的翻译字段

| 字段名 | 说明 | 示例 |
|--------|------|------|
| title | 标题 | "Attack on Titan" -> "进击的巨人" |
| synopsis | 剧情简介 | 长篇文本翻译 |
| type | 类型 | "TV" -> "TV动画" |
| season | 季 | "Winter" -> "冬季" |
| status | 状态 | "Finished" -> "已完结" |
| studios | 制作公司 | "Wit Studio" -> "Wit Studio" |
| genres | 类型标签 | "Action, Fantasy" -> "动作, 奇幻" |
| source | 来源 | "Manga" -> "漫画" |

### 使用示例

```json
{
  "batch_size": 10,
  "start_id": 15,
  "fields": ["synopsis"]
}
```

### 注意事项

- id可能不是连续的
- 如果字段值已经是中文，将保留原值
- 翻译过程中如果遇到错误，该字段将保留原值
- 批量处理时建议每次不超过50个实体
- MySQL凭据需要配置在环境中或项目配置中
- **文件写入策略**: 所有临时文件采用**覆盖写入**模式，每次执行时会覆盖已有文件
- 临时目录会自动清理（可选）
