import type {ReactNode} from 'react';
import {ErrorFeedback} from './Feedback';
export function Alert({children}:{children:ReactNode}){return <ErrorFeedback>{children}</ErrorFeedback>;}
