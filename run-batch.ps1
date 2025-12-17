$compose = Join-Path $PSScriptRoot "docker-compose.yml"
docker compose -f $compose up -d mb-be mb-auth | Out-Null

Start-Sleep -Seconds 60

docker compose -f $compose run --rm -T --entrypoint java mb-batch `
  -jar /app/app.jar `
  --spring.batch.job.enabled=true `
  --spring.batch.job.name=importTransactionsJob `
  --app.batch.import-transactions.input-resource=file:/data/transactions-source.txt `
  2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot "logs\batch.log") -Append
