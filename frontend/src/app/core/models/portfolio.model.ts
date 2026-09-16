export interface Holding {
    instrumentName: string;
    holdingValue: number;
  }
  
  export interface Portfolio {
    totalPortfolioValue: number;
    holdings: Holding[];
  }