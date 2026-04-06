#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-01-30 13:33
@Author  : zhangjunfan1997@naver.com
@File    : pushover
"""
import logging
import os

import requests
from dotenv import load_dotenv

load_dotenv()

# Pushover API 配置
PUSHOVER_API_URL = "https://api.pushover.net/1/messages.json"
PUSHOVER_API_TOKEN = os.getenv("PUSHOVER_API_TOKEN")  # 替换为你的 API Token
PUSHOVER_USER_KEY = os.getenv("PUSHOVER_USER_KEY")  # 替换为你的 User Key


# 发送通知的函数
def send_pushover_notification(title, message, sound="cashregister"):
    data = {
        "token": PUSHOVER_API_TOKEN,
        "user": PUSHOVER_USER_KEY,
        "title": title,
        "message": message,
        "sound": sound,  # 声效
        "priority": 2,
        "retry": 30,  # priority=2时的重试间隔秒数（至少为30）
        "expire": 10800,  # priority=2时的过期秒数
    }
    response = requests.post(PUSHOVER_API_URL, data=data)
    if response.status_code == 200:
        logging.info("Notification sent successfully!")
    else:
        logging.error("Failed to send notification: %s", response.text)


if __name__ == "__main__":
    send_pushover_notification("Title", "test message")
