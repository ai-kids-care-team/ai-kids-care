from dataclasses import dataclass
from typing import Optional, List, Dict

@dataclass
class FieldModel:
    name: str                 # java字段名，如 userName
    column: str               # 原列名，如 user_name
    java_type: str            # String/Long/UUID...
    required: bool            # NOT NULL
    max_len: Optional[int]    # varchar长度
    comment: Optional[str]    # 列注释
    pk: bool = False
    fk: Optional[Dict[str, str]] = None  # {"table": "...", "column": "..."}

@dataclass
class EntityModel:
    package_base: str
    schema: str
    table: str
    entity_name: str          # 类名：UserAccount
    resource: str             # 路径/权限资源名：/v1/ai_models
    id_type: str              # Long/UUID/...
    fields: List[FieldModel]