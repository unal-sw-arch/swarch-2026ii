from typing import List, Optional
from pydantic import BaseModel, Field


class ItemIn(BaseModel):
    dining_hall: str
    product_name: str
    category: str
    quantity: int = Field(ge=0)


class ItemUpdate(BaseModel):
    quantity: Optional[int] = Field(default=None, ge=0)
    product_name: Optional[str] = None
    category: Optional[str] = None


class RestockItem(BaseModel):
    product_name: str
    quantity: int = Field(default=1, ge=1)


class RestockIn(BaseModel):
    dining_hall: str
    items: List[RestockItem]


class ReportIn(BaseModel):
    dining_hall: str
