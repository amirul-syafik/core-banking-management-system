import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import './index.css'
import './cbms-tokens.css'
import './cbms-compact-typography.css'
import App from './App.tsx'
import './cbms-ui-consistency.css'
import './cbms-final-feedback.css'
import './cbms-last-review.css'
import './cbms-readonly-fields.css'
import './cbms-typography.css'
import './cbms-search-layout.css'

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <App/>
    </StrictMode>,
)
