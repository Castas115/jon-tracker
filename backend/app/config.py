from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./data/tracker.db"
    cors_origins: list[str] = ["http://localhost:5173", "http://raspi:5173"]

    # Secret iCal URL from Google Calendar (Settings → calendar →
    # "Secret address in iCal format"). Empty string disables the integration.
    ics_url: str = ""

    # OpenAI Whisper for /transcribe. Audio is never written to disk; it
    # streams straight to OpenAI.
    openai_api_key: str = ""
    whisper_model: str = "whisper-1"


settings = Settings()
