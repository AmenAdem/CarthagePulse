"""
Circuit Breaker implementation for external API calls
Provides resilience and fault tolerance for the scraper
"""
import asyncio
import time
from enum import Enum
from typing import Callable, Any, Optional
import structlog

logger = structlog.get_logger()


class CircuitState(Enum):
    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"


class CircuitBreakerError(Exception):
    """Exception raised when circuit breaker is open"""
    pass


class AsyncCircuitBreaker:
    """
    Async circuit breaker implementation with configurable thresholds
    """
    
    def __init__(
        self,
        failure_threshold: int = 5,
        timeout: float = 60.0,
        expected_exception: tuple = (Exception,),
        name: str = "default"
    ):
        self.failure_threshold = failure_threshold
        self.timeout = timeout
        self.expected_exception = expected_exception
        self.name = name
        
        self._failure_count = 0
        self._last_failure_time: Optional[float] = None
        self._state = CircuitState.CLOSED
        self._lock = asyncio.Lock()
    
    @property
    def state(self) -> CircuitState:
        return self._state
    
    @property
    def failure_count(self) -> int:
        return self._failure_count
    
    async def __aenter__(self):
        await self._check_state()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if exc_type is None:
            await self._on_success()
        elif issubclass(exc_type, self.expected_exception):
            await self._on_failure()
        else:
            # Unexpected exception, don't count as failure
            pass
        
        return False
    
    async def call(self, func: Callable, *args, **kwargs) -> Any:
        """
        Execute a function with circuit breaker protection
        """
        async with self:
            return await func(*args, **kwargs)
    
    async def _check_state(self) -> None:
        """
        Check and update circuit breaker state
        """
        async with self._lock:
            if self._state == CircuitState.OPEN:
                if self._should_attempt_reset():
                    self._state = CircuitState.HALF_OPEN
                    logger.info(
                        "Circuit breaker transitioning to half-open",
                        name=self.name
                    )
                else:
                    raise CircuitBreakerError(
                        f"Circuit breaker '{self.name}' is open"
                    )
    
    def _should_attempt_reset(self) -> bool:
        """
        Check if enough time has passed to attempt reset
        """
        if self._last_failure_time is None:
            return True
        
        return (time.time() - self._last_failure_time) >= self.timeout
    
    async def _on_success(self) -> None:
        """
        Handle successful operation
        """
        async with self._lock:
            if self._state == CircuitState.HALF_OPEN:
                self._reset()
                logger.info(
                    "Circuit breaker reset to closed state",
                    name=self.name
                )
    
    async def _on_failure(self) -> None:
        """
        Handle failed operation
        """
        async with self._lock:
            self._failure_count += 1
            self._last_failure_time = time.time()
            
            if self._failure_count >= self.failure_threshold:
                self._state = CircuitState.OPEN
                logger.warning(
                    "Circuit breaker opened due to failures",
                    name=self.name,
                    failure_count=self._failure_count,
                    threshold=self.failure_threshold
                )
    
    def _reset(self) -> None:
        """
        Reset circuit breaker to closed state
        """
        self._failure_count = 0
        self._last_failure_time = None
        self._state = CircuitState.CLOSED
    
    async def force_open(self) -> None:
        """
        Manually open the circuit breaker
        """
        async with self._lock:
            self._state = CircuitState.OPEN
            self._last_failure_time = time.time()
            logger.warning("Circuit breaker manually opened", name=self.name)
    
    async def force_close(self) -> None:
        """
        Manually close the circuit breaker
        """
        async with self._lock:
            self._reset()
            logger.info("Circuit breaker manually closed", name=self.name)


# Pre-configured circuit breakers for different services
STOCK_API_BREAKER = AsyncCircuitBreaker(
    failure_threshold=3,
    timeout=30.0,
    name="stock_api"
)

NEWS_API_BREAKER = AsyncCircuitBreaker(
    failure_threshold=5,
    timeout=60.0,
    name="news_api"
)

ECONOMIC_API_BREAKER = AsyncCircuitBreaker(
    failure_threshold=2,
    timeout=120.0,
    name="economic_api"
)