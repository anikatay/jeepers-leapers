# Jeepers Leapers
# Jeepers Leapers

## Setup

  ```bash
  # 1. Clone the repo
  git clone https://github.com/anikatay/jeepers-leapers.git
  cd jeepers-leapers
  ```
## Running With docker-compose

1.  docker-compose up -d --build 
  -   If app does not rebuild with new changes (docker is running the cached app):
  -   docker-compose up -d --build --force-recreate
2.  docker ps (make sure all containers are up and running)
3.  if all containers are running front end is accessible from "your-ip":8090

## Running the frontend

1.  cd into frontend directory
2.  npm install
3.  npm run start

##  Jupyter setup

- /usr/bin/python3 -m ensurepip --upgrade
- pip3 install notebook
- jupyter notebook

## Project Structure
```
jeepers-leapers/
├── frontend/
├──	src/
│	├──	main/
| | ├──java/com/neueda/leap/
| | | ├──controller/
| | | ├──dto/
| | | ├──model/
| | | ├──repository/
| | | └──Main.java
| | └──resources/
└ └──test/java/com/neueda/leap
```
## Key Dependencies

- Java 21       | Backend
- Maven 3.9     | Build backend
- PostgreSQL    | Database
- Nodejs10.9.8  | Build frontend
- Angular21.2.0 | frontend framework
- Docker        | Containerization
