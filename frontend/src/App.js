import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './contexts/AuthContext';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import CompanySelector from './pages/CompanySelector';
import Settings from './pages/Settings';
import Navbar from './components/Navbar';
import LoadingSpinner from './components/LoadingSpinner';
import { Container } from '@mui/material';

function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <Router>
      <div className="App">
        {user && <Navbar />}
        <Container maxWidth="xl" sx={{ mt: user ? 2 : 0 }}>
          <Routes>
            <Route 
              path="/login" 
              element={!user ? <Login /> : <Navigate to="/dashboard" />} 
            />
            <Route 
              path="/register" 
              element={!user ? <Register /> : <Navigate to="/dashboard" />} 
            />
            <Route 
              path="/dashboard" 
              element={user ? <Dashboard /> : <Navigate to="/login" />} 
            />
            <Route 
              path="/companies" 
              element={user ? <CompanySelector /> : <Navigate to="/login" />} 
            />
            <Route 
              path="/settings" 
              element={user ? <Settings /> : <Navigate to="/login" />} 
            />
            <Route 
              path="/" 
              element={<Navigate to={user ? "/dashboard" : "/login"} />} 
            />
          </Routes>
        </Container>
      </div>
    </Router>
  );
}

export default App;