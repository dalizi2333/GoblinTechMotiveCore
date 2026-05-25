#!/usr/bin/env python3
import os
import re
import sys
import json
from urllib.request import urlopen, Request
from urllib.parse import urlparse

GITHUB_REPO = "george8188625/Create-Electro-Energetics"
RELEASES_URL = f"https://api.github.com/repos/{GITHUB_REPO}/releases"
DOWNLOAD_DIR = "libs"
TARGET_FILENAME = "electroenergetics-latest.jar"
VERSION_REGEX = r"autobuild-(\d+)"

def get_latest_autobuild():
    print(f"Fetching releases from {GITHUB_REPO}...")
    headers = {"User-Agent": "GoblinTechMotive/1.0"}
    req = Request(RELEASES_URL, headers=headers)
    
    try:
        with urlopen(req, timeout=30) as response:
            releases = json.loads(response.read().decode())
    except Exception as e:
        print(f"Error fetching releases: {e}")
        return None, None
    
    latest_build = None
    latest_version = 0
    
    for release in releases:
        tag_name = release.get("tag_name", "")
        match = re.match(VERSION_REGEX, tag_name)
        if match:
            build_num = int(match.group(1))
            if build_num > latest_version:
                latest_version = build_num
                latest_build = release
    
    if not latest_build:
        print("No autobuild releases found")
        return None, None
    
    # Find the jar asset
    jar_url = None
    for asset in latest_build.get("assets", []):
        if asset["name"].endswith(".jar") and "electroenergetics" in asset["name"].lower():
            jar_url = asset["browser_download_url"]
            break
    
    if not jar_url:
        print("No jar asset found in latest autobuild")
        return None, None
    
    return jar_url, latest_version

def download_file(url, dest_path):
    print(f"Downloading {url}...")
    
    try:
        with urlopen(url, timeout=120) as response:
            total_size = int(response.headers.get("Content-Length", 0))
            downloaded = 0
            chunk_size = 8192
            
            with open(dest_path, "wb") as f:
                while True:
                    chunk = response.read(chunk_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        progress = (downloaded / total_size) * 100
                        print(f"\rProgress: {progress:.1f}% ({downloaded}/{total_size} bytes)", end="")
            
            if total_size > 0:
                print()
        return True
    except Exception as e:
        print(f"\nError downloading file: {e}")
        return False

def main():
    # Create download directory if needed
    if not os.path.exists(DOWNLOAD_DIR):
        os.makedirs(DOWNLOAD_DIR)
    
    # Get latest autobuild
    jar_url, version = get_latest_autobuild()
    if not jar_url:
        print("Failed to find latest autobuild")
        sys.exit(1)
    
    print(f"Found latest autobuild: version {version}")
    
    # Download to temp file first
    temp_path = os.path.join(DOWNLOAD_DIR, f"electroenergetics-temp.jar")
    if not download_file(jar_url, temp_path):
        sys.exit(1)
    
    # Rename to target filename
    target_path = os.path.join(DOWNLOAD_DIR, TARGET_FILENAME)
    
    # Remove old file if exists
    if os.path.exists(target_path):
        os.remove(target_path)
    
    # Rename temp to target
    os.rename(temp_path, target_path)
    
    print(f"Successfully downloaded electroenergetics autobuild-{version} to {target_path}")
    
    # Update forge.versions.toml with new version info
    toml_path = "gradle/forge.versions.toml"
    if os.path.exists(toml_path):
        with open(toml_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Update the version comment
        old_pattern = r"electroenergetics = \"[^\"]+\""
        new_version = f"electroenergetics = \"autobuild-{version}\""
        content = re.sub(old_pattern, new_version, content)
        
        with open(toml_path, "w", encoding="utf-8") as f:
            f.write(content)
        
        print(f"Updated {toml_path} with version autobuild-{version}")

if __name__ == "__main__":
    main()