import React from 'react';
import CommercialMap from './CommercialMap';
import './CommercialAnalysisPage.css';

interface CommercialAnalysisPageProps {
  currentUser: string | null;
}

const CommercialAnalysisPage: React.FC<CommercialAnalysisPageProps> = ({ currentUser }) => {
  return (
    <div className="commercial-analysis-page">
      <CommercialMap />
    </div>
  );
};

export default CommercialAnalysisPage;
