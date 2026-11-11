class VideoError(RuntimeError):
    def __init__(self, code: str, message: str, status: int = 503, retryable: bool = True, current_version: int | None = None):
        super().__init__(message)
        self.code = code
        self.status = status
        self.retryable = retryable
        self.current_version = current_version

    def to_dict(self) -> dict:
        result = {"code": self.code, "message": str(self), "retryable": self.retryable}
        if self.current_version is not None:
            result["current_metadata_version"] = self.current_version
        return result
