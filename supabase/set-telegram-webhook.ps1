# Tells Telegram where to send messages for the Kvit bot.
# Reads the bot token and webhook secret from supabase/functions/.env, so nothing secret is stored here.
# Run once after deploying the "telegram" function:  powershell -File supabase\set-telegram-webhook.ps1

$envFile = Join-Path $PSScriptRoot 'functions\.env'
if (-not (Test-Path $envFile)) { Write-Error "Not found: $envFile"; exit 1 }

$vars = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*([A-Z_]+)=(.*)$' } | ForEach-Object {
    $vars[$Matches[1]] = $Matches[2].Trim()
}

$project = 'eydepefymbczdxkycunr'
$body = @{
    url             = "https://$project.supabase.co/functions/v1/telegram"
    secret_token    = $vars['TELEGRAM_WEBHOOK_SECRET']
    allowed_updates = @('message')
} | ConvertTo-Json

$result = Invoke-RestMethod -Method Post -Uri "https://api.telegram.org/bot$($vars['TELEGRAM_BOT_TOKEN'])/setWebhook" -ContentType 'application/json' -Body $body
$result | ConvertTo-Json
