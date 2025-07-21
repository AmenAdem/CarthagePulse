import React from 'react';
import { 
  AppBar, 
  Toolbar, 
  Typography, 
  Button, 
  Box,
  Chip
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useSocket } from '../contexts/SocketContext';

const Navbar = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { connected } = useSocket();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
          Stock Tracker
        </Typography>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Chip 
            label={connected ? 'Connected' : 'Disconnected'} 
            color={connected ? 'success' : 'error'} 
            size="small"
          />
          
          <Button color="inherit" onClick={() => navigate('/dashboard')}>
            Dashboard
          </Button>
          
          <Button color="inherit" onClick={() => navigate('/companies')}>
            Companies
          </Button>
          
          <Button color="inherit" onClick={() => navigate('/settings')}>
            Settings
          </Button>
          
          <Typography variant="body2" sx={{ mr: 2 }}>
            Welcome, {user?.first_name}
          </Typography>
          
          <Button color="inherit" onClick={handleLogout}>
            Logout
          </Button>
        </Box>
      </Toolbar>
    </AppBar>
  );
};

export default Navbar;