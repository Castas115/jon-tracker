from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./data/tracker.db"
    cors_origins: list[str] = ["http://localhost:5173", "http://raspi:5173"]

    google_client_secrets_file: str = "credentials.json"
    google_token_file: str = "token.json"
    google_calendar_id: str = "primary"


settings = Settings()
