# OldPoint project rules

- Java 使用四空格缩进、120 列行宽，控制流语句体一律加花括号并展开，复杂表达式先提取有意义的局部变量。
- 修改时沿用周围代码风格，不做无关的全文件格式化，不改字符串内容、导入顺序或业务逻辑。

- Every player-facing Lua menu, dialog, option and tooltip string must use a `getText("UI_OldPoint_...")` key. Do not put Chinese literals in Lua UI code.
- Add each new key to both `42/media/lua/shared/Translate/CN/UI_CN.txt` and `EN/UI_EN.txt`, and to both matching `UI.json` files. Keep keys identical across the four files and save them as UTF-8.
- Before preparing a local install or Workshop package, inspect changed UI code for hardcoded visible text and confirm all referenced localization keys exist in Chinese and English. This recurring issue has caused garbled DEBUG menus.
- Synchronize GitHub `master` only when the owner actually uploads a version to Steam Workshop. Local test builds stay local.
- Do not force unseen rooms black by shading terrain alone. The 0.6.155 attempt left doors and models visible against black walls; any room-visibility change must handle terrain, openings, and object passes together and be checked in game before release.
- Wall proxy experiments must treat original ChunkMeshData and its native buffers as borrowed: never mutate or free them. Retire only owned replacement meshes through Viewpoint's render-thread retirement queue. Keep CPU/GPU caches bounded and retain the previous cutaway when source geometry is incomplete.
- ZombieBuddy advice bodies are inlined into foreign classes. They must call public bridge methods on public classes; never directly access OldPoint private/package-private fields, nested types, methods or compiler-generated lambda helpers from advice. Keep private state access inside normal public bridge methods. Validate cross-package bytecode access before installing any new patch.
