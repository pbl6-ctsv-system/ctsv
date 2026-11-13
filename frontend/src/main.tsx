import React from 'react';
import {createRoot} from 'react-dom/client';
import FoundationWorkspace from './features/foundation/FoundationWorkspace';
import './style.css';
createRoot(document.getElementById('root')!).render(<React.StrictMode><FoundationWorkspace/></React.StrictMode>);
