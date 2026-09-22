# Jeepers Leapers

## Setup

```bash
# 1. Clone the repo
git clone https://github.com/anikatay/jeepers-leapers.git
cd jeepers-leapers
```

## Running With Docker Compose

1. Run:

   ```bash
   docker-compose up -d --build
   ```

   * If app does not rebuild with new changes because Docker is running the cached app, run:

     ```bash
     docker-compose up -d --build --force-recreate
     ```

2. Check that all containers are up and running:

   ```bash
   docker ps
   ```

3. If all containers are running, the front end is accessible from:

   ```text
   "your-ip":8090
   ```

## Running the Frontend

1. Go into the frontend directory:

   ```bash
   cd frontend
   ```

2. Install dependencies:

   ```bash
   npm install
   ```

3. Start the frontend:

   ```bash
   npm run start
   ```

## Jupyter Setup

```bash
/usr/bin/python3 -m ensurepip --upgrade
pip3 install notebook
jupyter notebook
```

## Project Structure

```text
jeepers-leapers/
├── frontend/
├── src/
│   ├── main/
│   │   ├── java/com/neueda/leap/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   └── Main.java
│   │   └── resources/
│   └── test/java/com/neueda/leap/
```

## Key Dependencies

| Dependency     | Purpose            |
| -------------- | ------------------ |
| Java 21        | Backend            |
| Maven 3.9      | Build backend      |
| PostgreSQL     | Database           |
| Node.js 10.9.8 | Build frontend     |
| Angular 21.2.0 | Frontend framework |
| Docker         | Containerization   |
