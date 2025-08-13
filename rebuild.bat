@echo off
echo Running Maven build...
call mvnw.cmd clean install -DskipTests

if %errorlevel% neq 0 (
    echo Maven build failed!
    exit /b %errorlevel%
)

echo Maven build successful. Rebuilding and starting Docker containers...
docker-compose up --build -d

echo "Done."
