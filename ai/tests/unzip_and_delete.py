#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
@Time    : 2026-03-29 15:48
@Author  : zhangjunfan1997@naver.com
@File    : unzip_and_delete
"""
import os
import zipfile
from tqdm import tqdm


def find_zip_files(root_dir):
    zip_files = []
    for foldername, _, filenames in os.walk(root_dir):
        for filename in filenames:
            if filename.lower().endswith('.zip'):
                zip_files.append(os.path.join(foldername, filename))
    return zip_files


def unzip_and_delete(root_dir):
    zip_files = find_zip_files(root_dir)

    print(f"Found {len(zip_files)} zip files.")

    for zip_path in tqdm(zip_files, desc="Unzipping", unit="file"):
        foldername = os.path.dirname(zip_path)
        filename = os.path.basename(zip_path)
        extract_dir = os.path.join(foldername, os.path.splitext(filename)[0])

        try:
            os.makedirs(extract_dir, exist_ok=True)

            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(extract_dir)

            os.remove(zip_path)

        except Exception as e:
            print(f"\nError processing {zip_path}: {e}")


if __name__ == "__main__":
    root_directory = "./data/이상행동 CCTV 영상/01.폭행(assult)"  # ← 改成你的目录
    unzip_and_delete(root_directory)
