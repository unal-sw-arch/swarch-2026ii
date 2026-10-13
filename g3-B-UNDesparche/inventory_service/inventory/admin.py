from django.contrib import admin
from .models import Borrowing, Implement, Reserve, User

admin.site.register(User)
admin.site.register(Implement)
admin.site.register(Reserve)
admin.site.register(Borrowing)
