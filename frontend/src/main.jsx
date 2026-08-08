import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import './styles.css';

// import.meta.env.BASE_URL is Vite's resolved base ("./" by default). Setting
// it as the router basename keeps client-side links correct whether the SPA
// is served from "/" or from a subpath like "/frontend/dist/".
const basename = (import.meta.env && import.meta.env.BASE_URL) || '/';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter basename={basename}>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);