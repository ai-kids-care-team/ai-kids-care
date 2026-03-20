import re

def snake_to_pascal(name: str) -> str:
    parts = re.split(r"[_\s]+", name.strip())
    return "".join(p[:1].upper() + p[1:] for p in parts if p)

def snake_to_camel(name: str) -> str:
    pascal = snake_to_pascal(name)
    return pascal[:1].lower() + pascal[1:] if pascal else pascal

def singularize_last_token(table: str) -> str:
    """
    只对最后一个 token 做很轻量的英文单数化，满足 models->model, cameras->camera
    覆盖面不追求完美（比如 companies->company 也处理一下）。
    """
    toks = table.split("_")
    if not toks:
        return table
    last = toks[-1]
    if last.endswith("ies") and len(last) > 3:
        last = last[:-3] + "y"
    elif last.endswith("ses") and len(last) > 3:
        # classes -> class（粗略处理）
        last = last[:-2]
    elif last.endswith("s") and not last.endswith("ss") and len(last) > 1:
        last = last[:-1]
    toks[-1] = last
    return "_".join(toks)

def table_to_entity_name(table: str) -> str:
    return snake_to_pascal(singularize_last_token(table))

def resource_path_v1(table: str) -> str:
    # 按你要求：/api/v1/ai_models（直接用表名）
    return f"/api/v1/{table}"