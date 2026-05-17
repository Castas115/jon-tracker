from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./data/tracker.db"
    cors_origins: list[str] = ["http://localhost:5173", "http://raspi:5173"]

    # Secret iCal URL from Google Calendar (Settings → calendar →
    # "Secret address in iCal format"). Empty string disables the integration.
    ics_url: str = ""

    # Transcription for /transcribe. Audio streams to the provider, never
    # written to disk. Precedence: Groq → Azure OpenAI → OpenAI direct.
    groq_api_key: str = ""
    groq_model: str = "whisper-large-v3-turbo"
    openai_api_key: str = ""
    whisper_model: str = "whisper-1"
    azure_openai_endpoint: str = ""
    azure_openai_api_key: str = ""
    azure_openai_api_version: str = "2024-06-01"
    azure_openai_deployment_name: str = ""


settings = Settings()
