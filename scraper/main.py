import asyncio
import json
import logging
import os
import time
from datetime import datetime
from typing import List, Dict, Any

import aiohttp
import requests
from bs4 import BeautifulSoup
from kafka import KafkaProducer
import psycopg2
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class DataScraper:
    def __init__(self):
        self.kafka_producer = KafkaProducer(
            bootstrap_servers=[os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')],
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        
        self.alpha_vantage_key = os.getenv('ALPHA_VANTAGE_API_KEY', 'demo')
        self.news_api_key = os.getenv('NEWS_API_KEY', 'demo')
        
        # Database connection
        self.db_conn = psycopg2.connect(
            host=os.getenv('DB_HOST', 'localhost'),
            database=os.getenv('DB_NAME', 'stocktracker'),
            user=os.getenv('DB_USER', 'stockuser'),
            password=os.getenv('DB_PASS', 'stockpass'),
            port=os.getenv('DB_PORT', '5432')
        )
        
    async def scrape_stock_prices(self, symbols: List[str]):
        """Scrape stock prices using Alpha Vantage API"""
        async with aiohttp.ClientSession() as session:
            for symbol in symbols:
                try:
                    url = f"https://www.alphavantage.co/query"
                    params = {
                        'function': 'GLOBAL_QUOTE',
                        'symbol': symbol,
                        'apikey': self.alpha_vantage_key
                    }
                    
                    async with session.get(url, params=params) as response:
                        if response.status == 200:
                            data = await response.json()
                            await self.process_stock_data(symbol, data)
                        else:
                            logger.error(f"Failed to fetch data for {symbol}: {response.status}")
                    
                    # Rate limiting - Alpha Vantage free tier allows 5 calls per minute
                    await asyncio.sleep(12)
                    
                except Exception as e:
                    logger.error(f"Error scraping stock price for {symbol}: {e}")

    async def process_stock_data(self, symbol: str, data: Dict[str, Any]):
        """Process and store stock data"""
        try:
            if 'Global Quote' in data:
                quote = data['Global Quote']
                
                # Get company ID from database
                company_id = self.get_company_id(symbol)
                if not company_id:
                    logger.warning(f"Company not found for symbol: {symbol}")
                    return
                
                stock_data = {
                    'companyId': company_id,
                    'symbol': symbol,
                    'price': float(quote.get('05. price', 0)),
                    'volume': int(quote.get('06. volume', 0)),
                    'changePercent': float(quote.get('10. change percent', '0%').replace('%', '')),
                    'timestamp': datetime.now().isoformat(),
                    'source': 'alpha_vantage'
                }
                
                # Store in database
                self.store_stock_price(stock_data)
                
                # Send to Kafka
                self.kafka_producer.send('stock-prices', stock_data)
                logger.info(f"Sent stock data for {symbol}: ${stock_data['price']}")
                
        except Exception as e:
            logger.error(f"Error processing stock data for {symbol}: {e}")

    async def scrape_news(self, companies: List[Dict[str, Any]]):
        """Scrape news using News API"""
        try:
            for company in companies:
                url = "https://newsapi.org/v2/everything"
                params = {
                    'q': f"{company['name']} OR {company['symbol']}",
                    'language': 'en',
                    'sortBy': 'publishedAt',
                    'pageSize': 5,
                    'apiKey': self.news_api_key
                }
                
                response = requests.get(url, params=params)
                if response.status_code == 200:
                    data = response.json()
                    await self.process_news_data(company, data)
                    
                # Rate limiting
                await asyncio.sleep(1)
                
        except Exception as e:
            logger.error(f"Error scraping news: {e}")

    async def process_news_data(self, company: Dict[str, Any], data: Dict[str, Any]):
        """Process and store news data"""
        try:
            articles = data.get('articles', [])
            
            for article in articles:
                news_data = {
                    'companyId': company['id'],
                    'symbol': company['symbol'],
                    'title': article.get('title', ''),
                    'content': article.get('description', ''),
                    'url': article.get('url', ''),
                    'source': article.get('source', {}).get('name', ''),
                    'publishedAt': article.get('publishedAt', ''),
                    'sentimentScore': self.analyze_sentiment(article.get('title', '') + ' ' + article.get('description', '')),
                    'timestamp': datetime.now().isoformat()
                }
                
                # Store in database
                self.store_news_article(news_data)
                
                # Send to Kafka
                self.kafka_producer.send('news-articles', news_data)
                logger.info(f"Sent news for {company['symbol']}: {news_data['title'][:50]}...")
                
        except Exception as e:
            logger.error(f"Error processing news data: {e}")

    def analyze_sentiment(self, text: str) -> float:
        """Simple sentiment analysis (placeholder for more sophisticated analysis)"""
        positive_words = ['good', 'great', 'excellent', 'positive', 'up', 'rise', 'gain', 'profit', 'success']
        negative_words = ['bad', 'terrible', 'negative', 'down', 'fall', 'loss', 'decline', 'poor', 'fail']
        
        text_lower = text.lower()
        positive_count = sum(1 for word in positive_words if word in text_lower)
        negative_count = sum(1 for word in negative_words if word in text_lower)
        
        total_count = positive_count + negative_count
        if total_count == 0:
            return 0.0
        
        return (positive_count - negative_count) / total_count

    def get_company_id(self, symbol: str) -> int:
        """Get company ID from database"""
        try:
            cursor = self.db_conn.cursor()
            cursor.execute("SELECT id FROM companies WHERE symbol = %s", (symbol,))
            result = cursor.fetchone()
            cursor.close()
            return result[0] if result else None
        except Exception as e:
            logger.error(f"Error getting company ID for {symbol}: {e}")
            return None

    def get_tracked_companies(self) -> List[Dict[str, Any]]:
        """Get all companies from database"""
        try:
            cursor = self.db_conn.cursor()
            cursor.execute("SELECT id, symbol, name FROM companies")
            results = cursor.fetchall()
            cursor.close()
            
            return [
                {'id': row[0], 'symbol': row[1], 'name': row[2]}
                for row in results
            ]
        except Exception as e:
            logger.error(f"Error getting tracked companies: {e}")
            return []

    def store_stock_price(self, data: Dict[str, Any]):
        """Store stock price in database"""
        try:
            cursor = self.db_conn.cursor()
            cursor.execute("""
                INSERT INTO stock_prices (company_id, price, volume, timestamp, source)
                VALUES (%s, %s, %s, %s, %s)
            """, (
                data['companyId'],
                data['price'],
                data['volume'],
                data['timestamp'],
                data['source']
            ))
            self.db_conn.commit()
            cursor.close()
        except Exception as e:
            logger.error(f"Error storing stock price: {e}")
            self.db_conn.rollback()

    def store_news_article(self, data: Dict[str, Any]):
        """Store news article in database"""
        try:
            cursor = self.db_conn.cursor()
            
            # Check if article already exists
            cursor.execute("SELECT id FROM news_articles WHERE url = %s", (data['url'],))
            if cursor.fetchone():
                cursor.close()
                return  # Article already exists
            
            cursor.execute("""
                INSERT INTO news_articles (company_id, title, content, url, source, sentiment_score, published_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """, (
                data['companyId'],
                data['title'],
                data['content'],
                data['url'],
                data['source'],
                data['sentimentScore'],
                data['publishedAt']
            ))
            self.db_conn.commit()
            cursor.close()
        except Exception as e:
            logger.error(f"Error storing news article: {e}")
            self.db_conn.rollback()

    async def scrape_economic_indicators(self):
        """Scrape economic indicators (placeholder implementation)"""
        try:
            # This would integrate with FRED API or other economic data sources
            indicators = [
                {'name': 'GDP Growth', 'value': 2.1, 'unit': '%'},
                {'name': 'Unemployment Rate', 'value': 3.7, 'unit': '%'},
                {'name': 'Inflation Rate', 'value': 3.2, 'unit': '%'},
            ]
            
            for indicator in indicators:
                economic_data = {
                    'indicatorName': indicator['name'],
                    'value': indicator['value'],
                    'unit': indicator['unit'],
                    'timestamp': datetime.now().isoformat(),
                    'source': 'placeholder'
                }
                
                # Send to Kafka
                self.kafka_producer.send('economic-indicators', economic_data)
                logger.info(f"Sent economic indicator: {indicator['name']}")
                
        except Exception as e:
            logger.error(f"Error scraping economic indicators: {e}")

    async def run_scraping_cycle(self):
        """Run a complete scraping cycle"""
        logger.info("Starting scraping cycle...")
        
        try:
            # Get tracked companies
            companies = self.get_tracked_companies()
            symbols = [company['symbol'] for company in companies]
            
            if not companies:
                logger.warning("No companies found to track")
                return
            
            # Scrape stock prices
            await self.scrape_stock_prices(symbols)
            
            # Scrape news
            await self.scrape_news(companies)
            
            # Scrape economic indicators
            await self.scrape_economic_indicators()
            
            logger.info("Scraping cycle completed successfully")
            
        except Exception as e:
            logger.error(f"Error in scraping cycle: {e}")

    def close(self):
        """Close connections"""
        self.kafka_producer.close()
        self.db_conn.close()

async def main():
    scraper = DataScraper()
    
    try:
        # Run initial scrape
        await scraper.run_scraping_cycle()
        
        # Set up periodic scraping
        scrape_interval = int(os.getenv('SCRAPER_INTERVAL', '60'))  # seconds
        
        while True:
            await asyncio.sleep(scrape_interval)
            await scraper.run_scraping_cycle()
            
    except KeyboardInterrupt:
        logger.info("Scraper stopped by user")
    except Exception as e:
        logger.error(f"Scraper error: {e}")
    finally:
        scraper.close()

if __name__ == "__main__":
    asyncio.run(main())