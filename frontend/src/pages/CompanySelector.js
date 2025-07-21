import React, { useState, useEffect } from 'react';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  Chip,
  Box,
  Alert,
  TextField,
  InputAdornment
} from '@mui/material';
import { Search, Add, Remove } from '@mui/icons-material';
import { companiesAPI } from '../services/api';

const CompanySelector = () => {
  const [allCompanies, setAllCompanies] = useState([]);
  const [trackedCompanies, setTrackedCompanies] = useState([]);
  const [filteredCompanies, setFilteredCompanies] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    filterCompanies();
  }, [searchTerm, allCompanies]);

  const loadData = async () => {
    try {
      const [companiesResponse, trackedResponse] = await Promise.all([
        companiesAPI.getAll(),
        companiesAPI.getTracked()
      ]);
      
      setAllCompanies(companiesResponse.data);
      setTrackedCompanies(trackedResponse.data);
    } catch (error) {
      setError('Failed to load companies');
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const filterCompanies = () => {
    const filtered = allCompanies.filter(company =>
      company.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      company.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
      company.sector?.toLowerCase().includes(searchTerm.toLowerCase())
    );
    setFilteredCompanies(filtered);
  };

  const isTracked = (companyId) => {
    return trackedCompanies.some(tc => tc.id === companyId);
  };

  const handleTrack = async (company) => {
    try {
      await companiesAPI.track(company.id);
      setTrackedCompanies([...trackedCompanies, company]);
    } catch (error) {
      console.error('Error tracking company:', error);
      setError('Failed to track company');
    }
  };

  const handleUntrack = async (companyId) => {
    try {
      await companiesAPI.untrack(companyId);
      setTrackedCompanies(trackedCompanies.filter(tc => tc.id !== companyId));
    } catch (error) {
      console.error('Error untracking company:', error);
      setError('Failed to untrack company');
    }
  };

  if (loading) {
    return <Typography>Loading companies...</Typography>;
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Select Companies to Track
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ mb: 3 }}>
        <TextField
          fullWidth
          placeholder="Search companies by name, symbol, or sector..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      <Typography variant="h6" gutterBottom>
        Currently Tracking: {trackedCompanies.length} companies
      </Typography>

      <Grid container spacing={2}>
        {filteredCompanies.map((company) => (
          <Grid item xs={12} sm={6} md={4} key={company.id}>
            <Card>
              <CardContent>
                <Box display="flex" justifyContent="space-between" alignItems="start" mb={1}>
                  <Typography variant="h6" component="div">
                    {company.symbol}
                  </Typography>
                  <Chip 
                    label={company.sector || 'N/A'} 
                    size="small" 
                    color="secondary"
                  />
                </Box>
                
                <Typography variant="body1" gutterBottom>
                  {company.name}
                </Typography>
                
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {company.description?.substring(0, 100)}...
                </Typography>
                
                <Box mt={2}>
                  {isTracked(company.id) ? (
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<Remove />}
                      onClick={() => handleUntrack(company.id)}
                      fullWidth
                    >
                      Stop Tracking
                    </Button>
                  ) : (
                    <Button
                      variant="contained"
                      color="primary"
                      startIcon={<Add />}
                      onClick={() => handleTrack(company)}
                      fullWidth
                    >
                      Start Tracking
                    </Button>
                  )}
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {filteredCompanies.length === 0 && (
        <Typography variant="body1" color="text.secondary" textAlign="center" mt={4}>
          No companies found matching your search criteria.
        </Typography>
      )}
    </Box>
  );
};

export default CompanySelector;