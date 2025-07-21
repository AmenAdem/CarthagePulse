import React, { useState, useEffect } from 'react';
import {
  Paper,
  Typography,
  Switch,
  FormControlLabel,
  TextField,
  Button,
  Box,
  Alert,
  Divider,
  Slider
} from '@mui/material';
import { userAPI } from '../services/api';

const Settings = () => {
  const [preferences, setPreferences] = useState({
    email_enabled: true,
    sms_enabled: false,
    push_enabled: true,
    price_change_threshold: 5.0,
    phone_number: ''
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    loadPreferences();
  }, []);

  const loadPreferences = async () => {
    try {
      const response = await userAPI.getPreferences();
      setPreferences(response.data);
    } catch (error) {
      setError('Failed to load preferences');
      console.error('Error loading preferences:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setMessage('');
    setError('');

    try {
      await userAPI.updatePreferences(preferences);
      setMessage('Preferences saved successfully!');
    } catch (error) {
      setError('Failed to save preferences');
      console.error('Error saving preferences:', error);
    } finally {
      setSaving(false);
    }
  };

  const handleChange = (field, value) => {
    setPreferences(prev => ({
      ...prev,
      [field]: value
    }));
  };

  if (loading) {
    return <Typography>Loading settings...</Typography>;
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Settings
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          Notification Preferences
        </Typography>

        {message && <Alert severity="success" sx={{ mb: 2 }}>{message}</Alert>}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <Box sx={{ mb: 3 }}>
          <FormControlLabel
            control={
              <Switch
                checked={preferences.email_enabled}
                onChange={(e) => handleChange('email_enabled', e.target.checked)}
              />
            }
            label="Email Notifications"
          />
          <Typography variant="body2" color="text.secondary">
            Receive notifications via email
          </Typography>
        </Box>

        <Box sx={{ mb: 3 }}>
          <FormControlLabel
            control={
              <Switch
                checked={preferences.push_enabled}
                onChange={(e) => handleChange('push_enabled', e.target.checked)}
              />
            }
            label="Push Notifications"
          />
          <Typography variant="body2" color="text.secondary">
            Receive real-time notifications in the app
          </Typography>
        </Box>

        <Box sx={{ mb: 3 }}>
          <FormControlLabel
            control={
              <Switch
                checked={preferences.sms_enabled}
                onChange={(e) => handleChange('sms_enabled', e.target.checked)}
              />
            }
            label="SMS Notifications"
          />
          <Typography variant="body2" color="text.secondary">
            Receive notifications via SMS (requires phone number)
          </Typography>
        </Box>

        {preferences.sms_enabled && (
          <Box sx={{ mb: 3 }}>
            <TextField
              fullWidth
              label="Phone Number"
              value={preferences.phone_number || ''}
              onChange={(e) => handleChange('phone_number', e.target.value)}
              placeholder="+1234567890"
              helperText="Include country code"
            />
          </Box>
        )}

        <Divider sx={{ my: 3 }} />

        <Typography variant="h6" gutterBottom>
          Alert Settings
        </Typography>

        <Box sx={{ mb: 3 }}>
          <Typography gutterBottom>
            Price Change Threshold: {preferences.price_change_threshold}%
          </Typography>
          <Slider
            value={preferences.price_change_threshold}
            onChange={(e, value) => handleChange('price_change_threshold', value)}
            min={1}
            max={20}
            step={0.5}
            marks={[
              { value: 1, label: '1%' },
              { value: 5, label: '5%' },
              { value: 10, label: '10%' },
              { value: 20, label: '20%' },
            ]}
            valueLabelDisplay="auto"
          />
          <Typography variant="body2" color="text.secondary">
            Receive alerts when stock price changes by this percentage or more
          </Typography>
        </Box>

        <Box sx={{ mt: 4 }}>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? 'Saving...' : 'Save Preferences'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

export default Settings;