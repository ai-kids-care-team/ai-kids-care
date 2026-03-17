```
# 查看参数
npm install @openapitools/openapi-generator-cli -g
openapi-generator-cli version-manager set 7.20.0

openapi-generator-cli config-help -g spring

cd ./openapi-templates
openapi-generator-cli author template -g spring -o ./spring

openapi-generator-cli generate -c .\openapi-spring.yaml

Remove-Item -Recurse -Force ".\generated\"

```

https://github.com/OpenAPITools/openapi-generator/blob/master/modules/openapi-generator/src/main/java/org/openapitools/codegen/languages/SpringCodegen.java