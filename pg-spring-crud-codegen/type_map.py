def pg_to_java(data_type: str, udt_name: str | None = None) -> str:
    """
    data_type: information_schema.columns.data_type
    udt_name:  information_schema.columns.udt_name (更细：如 int8、varchar、timestamptz)
    """
    t = (udt_name or data_type or "").lower()

    mapping = {
        "int8": "Long",
        "bigint": "Long",
        "int4": "Integer",
        "integer": "Integer",
        "int2": "Short",
        "smallint": "Short",
        "bool": "Boolean",
        "boolean": "Boolean",
        "uuid": "UUID",
        "text": "String",
        "varchar": "String",
        "bpchar": "String",      # char(n)
        "numeric": "BigDecimal",
        "float8": "Double",
        "float4": "Float",
        "date": "LocalDate",
        "timestamp": "LocalDateTime",
        "timestamptz": "OffsetDateTime",
        "jsonb": "JsonNode",     # 或 String
        "json": "JsonNode",
    }
    return mapping.get(t, "String")