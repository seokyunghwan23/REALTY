import React, { useState } from 'react';
import TaxCalculatorPage from './components/TaxCalculatorPage';
import BuildingAnalysisPage from './components/BuildingAnalysisPage';
import './App.css';

type Tab = 'tax' | 'building';

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('tax');

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-content">
          <h1>부동산 계산기</h1>
          <nav className="app-tab-nav">
            <button
              className={`app-tab${activeTab === 'tax' ? ' active' : ''}`}
              onClick={() => setActiveTab('tax')}
            >
              세금 계산기
            </button>
            <button
              className={`app-tab${activeTab === 'building' ? ' active' : ''}`}
              onClick={() => setActiveTab('building')}
            >
              건물 분석
            </button>
          </nav>
        </div>
      </header>
      <main className="app-main">
        {activeTab === 'tax'      && <TaxCalculatorPage />}
        {activeTab === 'building' && <BuildingAnalysisPage />}
      </main>
    </div>
  );
}

export default App;
