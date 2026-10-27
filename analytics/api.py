import asyncio
import logging
import os
from contextlib import asynccontextmanager, suppress

from fastapi import FastAPI

from etl.run_pipeline import run_pipeline


logger = logging.getLogger("analytics-api")
REFRESH_INTERVAL_SECONDS = max(1, int(os.getenv("ETL_INTERVAL_SECONDS", "600")))


async def scheduled_refresh() -> None:
    while True:
        result = await asyncio.to_thread(run_pipeline)
        logger.info("Scheduled analytics refresh finished with status %s", result.get("status"))
        await asyncio.sleep(REFRESH_INTERVAL_SECONDS)


@asynccontextmanager
async def lifespan(app: FastAPI):
    refresh_task = asyncio.create_task(scheduled_refresh())
    try:
        yield
    finally:
        refresh_task.cancel()
        with suppress(asyncio.CancelledError):
            await refresh_task


app = FastAPI(title="Trading Analytics ETL", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/refresh")
def refresh() -> dict:
    """Run a synchronous analytics refresh and return its pipeline result."""
    return run_pipeline()