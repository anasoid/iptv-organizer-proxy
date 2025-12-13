# Mockoon - Xtream Codes API Mock Setup

Complete mock API setup for testing Xtream Codes `player_api.php` endpoints.

## 📁 Folder Structure

```
mockoon/
├── README.md                          # Main overview (read this first)
├── QUICKSTART.md                      # 5-minute quick start guide
├── SETUP.md                           # Detailed setup and troubleshooting
├── INDEX.md                           # This file - folder overview
│
├── mockoon-xtream-collection.json     # Mockoon API definitions
├── docker-compose.yml                 # Docker setup for CI/CD
├── .env.testing.example               # Environment config template
│
└── scripts/
    └── mockoon.sh                     # Helper script (start/stop/manage)
```

## 📚 Documentation Guide

| File | Purpose | Read When |
|------|---------|-----------|
| **README.md** | Full feature overview | First time setup |
| **QUICKSTART.md** | Fast 5-min setup | You want quick start |
| **SETUP.md** | Detailed guide | You need full details |
| **INDEX.md** | This file | Understanding structure |

## 🚀 Quick Links

### Getting Started
- **Local Testing**: See [QUICKSTART.md](./QUICKSTART.md) - Local section
- **Docker/CI/CD**: See [QUICKSTART.md](./QUICKSTART.md) - Docker section
- **Full Details**: See [SETUP.md](./SETUP.md)

### Common Tasks
- **Start mock server**: `./mockoon/scripts/mockoon.sh start`
- **Check status**: `./mockoon/scripts/mockoon.sh status`
- **Stop server**: `./mockoon/scripts/mockoon.sh stop`
- **View logs**: `./mockoon/scripts/mockoon.sh logs`

### Configuration
- **Environment vars**: See `.env.testing.example`
- **Docker setup**: See `docker-compose.yml`
- **Mock definitions**: See `mockoon-xtream-collection.json`

## 📖 Documentation Files

### README.md
Complete overview of the mock setup including:
- Feature list
- Quick start (local + Docker)
- What's included
- Architecture diagram
- Configuration
- Usage examples
- Performance notes
- Summary table

**Time to read**: 10-15 minutes

### QUICKSTART.md
Fast reference guide including:
- 5-minute local setup
- Docker setup for CI/CD
- What was created
- API endpoints
- Adding endpoints
- Using in code
- Testing strategies
- Common commands
- Troubleshooting
- Next steps

**Time to read**: 5-10 minutes
**Best for**: Getting started quickly

### SETUP.md
Comprehensive detailed guide including:
- Local setup (step-by-step)
- Docker setup (multiple methods)
- Environment configuration
- PHP test examples (unit & integration)
- Adding new endpoints
- Testing different scenarios
- CI/CD integration (GitHub Actions, GitLab CI)
- Full troubleshooting section
- Best practices

**Time to read**: 30-45 minutes
**Best for**: Deep dive understanding, troubleshooting

### INDEX.md
This file - folder structure and navigation guide

**Time to read**: 2-3 minutes
**Best for**: Understanding what's where

## 🔧 Files Reference

### mockoon-xtream-collection.json
Mockoon collection file containing:
- Authentication endpoint
- get_live_categories endpoint
- get_live_streams endpoint (with category filtering)
- Multiple response scenarios (success, empty)
- Latency simulation (50-150ms)
- CORS configuration

**Usage**: Import into Mockoon GUI or use with Docker

### docker-compose.yml
Docker configuration for:
- Mockoon service on port 3000
- Health checks
- Network configuration
- Optional app service setup

**Usage**: `docker-compose -f mockoon/docker-compose.yml up`

### .env.testing.example
Template environment variables:
```ini
APP_ENV=testing
XTREAM_BASE_URL=http://localhost:3000
XTREAM_USERNAME=testuser
XTREAM_PASSWORD=testpass
```

**Usage**: Copy to `.env.testing` and adjust as needed

### scripts/mockoon.sh
Helper script for managing mock server with commands:
- `start` - Start mock in Docker
- `stop` - Stop mock server
- `restart` - Restart mock server
- `status` - Check if running
- `logs` - View server logs

**Usage**: `./mockoon/scripts/mockoon.sh [command]`

## ⚡ Quick Start (Choose One)

### Option 1: Local Development (No Docker)
```bash
# 1. Open Mockoon desktop app
# 2. File → Import environment → select mockoon/mockoon-xtream-collection.json
# 3. Click green play button
# 4. Test: curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"
```

### Option 2: Docker (For CI/CD)
```bash
# Start
./mockoon/scripts/mockoon.sh start

# Test
curl "http://localhost:3000/player_api.php?username=testuser&password=testpass"

# Stop
./mockoon/scripts/mockoon.sh stop
```

## 🔗 Cross-References

Related files in the project:
- **Test suite**: `../back/tests/Feature/Services/Xtream/XtreamClientMockTest.php`
- **Xtream client**: `../back/src/Services/Xtream/XtreamClient.php`
- **Sync service**: `../back/src/Services/SyncService.php`

## 📝 Summary

| Aspect | Details |
|--------|---------|
| **Purpose** | Mock Xtream Codes API for testing |
| **Endpoints Mocked** | 3 (auth, get_live_categories, get_live_streams) |
| **Local Setup Time** | ~5 minutes |
| **Docker Setup Time** | ~2 minutes |
| **Port** | 3000 |
| **Collection File** | mockoon-xtream-collection.json |
| **Docker Image** | mockoon/mockoon:latest |
| **Supported Scenarios** | Success, empty, errors |
| **Memory Efficient** | Yes (via streaming) |
| **CI/CD Ready** | Yes |
| **Production Safe** | Yes (uses env variables) |

## ✅ Next Steps

1. Read [README.md](./README.md) for overview (10 min)
2. Follow [QUICKSTART.md](./QUICKSTART.md) (5 min)
3. Import collection into Mockoon (2 min)
4. Run tests locally (5 min)
5. Optional: Set up Docker for CI/CD (5 min)

## 🆘 Need Help?

- **Quick answers**: See [QUICKSTART.md](./QUICKSTART.md) "Troubleshooting" section
- **Deep dive**: See [SETUP.md](./SETUP.md) "Troubleshooting" section
- **External**: https://docs.mockoon.com

---

**Status**: ✅ Ready to use!

Start with [QUICKSTART.md](./QUICKSTART.md) for a quick setup, or [README.md](./README.md) for full overview.
