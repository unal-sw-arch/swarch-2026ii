from app.video_errors import VideoError


class UserError(VideoError):
    def __init__(self, code: str, message: str, status: int = 503, retryable: bool = True, **extra):
        super().__init__(code, message, status, retryable)
        self.extra = extra

    def to_dict(self) -> dict:
        return {**super().to_dict(), **self.extra}


def profile_state_error(error: VideoError) -> UserError:
    if isinstance(error, UserError):
        return error
    if error.code == "VIDEO_BUSY":
        return UserError("BUSY", "Another request is updating this user", 429)
    return UserError("PROFILE_STATE_UNAVAILABLE", "Could not read or confirm the persistent user journal")
