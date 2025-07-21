import React, { useState, useEffect } from 'react';
import {
  Grid,
  Paper,
  Typography,
  Box,
  Alert,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemText,
  Chip
} from '@mui/material';
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { companiesAPI } from '../services/api';
import { useSocket } from '../contexts/SocketContext';
import { format } from 'date-fns';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

const Dashboard = () => {
  const [trackedCompanies, setTrackedCompanies] = useState([]);
  const [stockData, setStockData] = useState({});
  const [newsData, setNewsData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const { socket, connected, subscribeToCompanies } = useSocket();

  useEffect(() => {
    loadTrackedCompanies();
  }, []);

  useEffect(() => {
    if (socket && connected) {
      socket.on('price-update', handlePriceUpdate);
      socket.on('news-update', handleNewsUpdate);
      
      return () => {
        socket.off('price-update');
        socket.off('news-update');
      };
    }
  }, [socket, connected]);

  useEffect(() => {
    if (trackedCompanies.length > 0 && connected) {
      const companyIds = trackedCompanies.map(c => c.id);
      subscribeToCompanies(companyIds);
      loadStockData();
      loadNews();
    }
  }, [trackedCompanies, connected]);

  const loadTrackedCompanies = async () => {
    try {
      const response = await companiesAPI.getTracked();
      setTrackedCompanies(response.data);
    } catch (error) {
      setError('Failed to load tracked companies');
      console.error('Error loading companies:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadStockData = async () => {
    try {
      const promises = trackedCompanies.map(async (company) => {
        const response = await companiesAPI.getPrices(company.id, { limit: 50 });
        return { companyId: company.id, prices: response.data };
      });
      
      const results = await Promise.all(promises);
      const stockDataMap = {};
      
      results.forEach(({ companyId, prices }) => {
        stockDataMap[companyId] = prices;
      });
      
      setStockData(stockDataMap);
    } catch (error) {
      console.error('Error loading stock data:', error);
    }
  };

  const loadNews = async () => {
    try {
      const promises = trackedCompanies.map(async (company) => {
        const response = await companiesAPI.getNews(company.id, { limit: 5 });
        return response.data.map(news => ({ ...news, company }));
      });
      
      const results = await Promise.all(promises);
      const allNews = results.flat().sort((a, b) => 
        new Date(b.published_at) - new Date(a.published_at)
      );
      
      setNewsData(allNews.slice(0, 10));
    } catch (error) {
      console.error('Error loading news:', error);
    }
  };

  const handlePriceUpdate = (data) => {
    setStockData(prev => ({
      ...prev,
      [data.companyId]: [data, ...(prev[data.companyId] || [])].slice(0, 50)
    }));
  };

  const handleNewsUpdate = (data) => {
    const company = trackedCompanies.find(c => c.id === data.companyId);
    if (company) {
      setNewsData(prev => [{ ...data, company }, ...prev].slice(0, 10));
    }
  };

  const getChartData = (companyId, companyName) => {
    const prices = stockData[companyId] || [];
    const reversedPrices = [...prices].reverse();
    
    return {
      labels: reversedPrices.map(p => format(new Date(p.timestamp), 'HH:mm')),
      datasets: [
        {
          label: companyName,
          data: reversedPrices.map(p => p.price),
          borderColor: 'rgb(75, 192, 192)',
          backgroundColor: 'rgba(75, 192, 192, 0.2)',
          tension: 0.1,
        },
      ],
    };
  };

  const chartOptions = {
    responsive: true,
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: true,
        text: 'Real-time Stock Price',
      },
    },
    scales: {
      y: {
        beginAtZero: false,
      },
    },
  };

  if (loading) {
    return <Typography>Loading dashboard...</Typography>;
  }

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  if (trackedCompanies.length === 0) {
    return (
      <Alert severity="info">
        You are not tracking any companies yet. Go to the Companies page to start tracking stocks.
      </Alert>
    );
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>

      <Grid container spacing={3}>
        {/* Stock Charts */}
        {trackedCompanies.map((company) => (
          <Grid item xs={12} md={6} key={company.id}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                {company.name} ({company.symbol})
              </Typography>
              {stockData[company.id] && stockData[company.id].length > 0 ? (
                <Line 
                  data={getChartData(company.id, company.name)} 
                  options={chartOptions} 
                />
              ) : (
                <Typography>No price data available</Typography>
              )}
            </Paper>
          </Grid>
        ))}

        {/* News Feed */}
        <Grid item xs={12}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Latest News
            </Typography>
            <List>
              {newsData.map((news, index) => (
                <ListItem key={index} divider>
                  <ListItemText
                    primary={
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="subtitle1">
                          {news.title}
                        </Typography>
                        <Chip 
                          label={news.company.symbol} 
                          size="small" 
                          color="primary" 
                        />
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography variant="body2" color="text.secondary">
                          {news.content?.substring(0, 200)}...
                        </Typography>
                        <Typography variant="caption" display="block">
                          {format(new Date(news.published_at), 'PPpp')} • {news.source}
                        </Typography>
                      </Box>
                    }
                  />
                </ListItem>
              ))}
            </List>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default Dashboard;