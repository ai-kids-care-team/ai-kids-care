```
openapi-generator-cli author template -g spring -o openapi-templates/spring

openapi-generator-cli generate -c .\openapi-templates\openapi-spring.yaml

Remove-Item -Recurse -Force ".\generated\"
```