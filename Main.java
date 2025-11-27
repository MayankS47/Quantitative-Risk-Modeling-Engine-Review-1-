import java.util.*;
import java.io.*;

// ---------------- SIMULATED JDBC CLASSES (Compiler Safe) ----------------

// Simple interface representing JDBC-like operations
interface DatabaseOperations {
    void connect() throws IOException;
    void insertLog(String msg);
    void disconnect();
}

// Simulated DB connection class
class DBManager implements DatabaseOperations {

    private boolean connected = false;

    @Override
    public void connect() throws IOException {
        connected = true;
        System.out.println("[DB] Connected (Simulated)");
    }

    @Override
    public void insertLog(String msg) {
        if (!connected) {
            System.out.println("[DB] Not connected. Cannot log.");
            return;
        }
        System.out.println("[DB] LOG: " + msg);
    }

    @Override
    public void disconnect() {
        connected = false;
        System.out.println("[DB] Disconnected (Simulated)");
    }
}

// DAO class using Generics
class StockDAO<T> {
    private final List<T> items = Collections.synchronizedList(new ArrayList<>());

    public void save(T item) {
        items.add(item);
    }

    public List<T> getAll() {
        return items;
    }
}



// ----------- Stock Class (Represents a single stock) -------------
// This class stores a stock symbol and its current price.
// It also allows updating the price when the market changes.
class Stock {

    private final String symbol;     // Stock ticker symbol (AAPL, GOOG)
    private double price;            // Current stock price

    // Constructor initializes the stock with a symbol and price
    public Stock(String s, double p){ 
        symbol = s;   
        price = p;    
    }

    // Getter for symbol
    public String getSymbol(){ 
        return symbol; 
    }

    // Getter for price
    public double getPrice(){ 
        return price;  
    }

    // Setter for price (after stress simulation)
    public void setPrice(double p){ 
        price = p;     
    }
}



// OOP Extension: A special type of stock using inheritance
class TechStock extends Stock {
    public TechStock(String s, double p) {
        super(s, p);
    }
}



// ----------- Portfolio Class (Holds stock quantities) -------------
// This class stores how many shares of each stock the user owns.
// It also stores an initial capital reference for risk evaluation.
class Portfolio {

    private final Map<String,Integer> holdings;  // Stores stock → quantity
    private final double initialCapital = 100000; // Reference value

    // Constructor copies holdings
    public Portfolio(Map<String,Integer> h){ 
        holdings = new HashMap<>(h); 
    }

    // Returns the holdings map
    public Map<String,Integer> getHoldings(){ 
        return holdings; 
    }

    // Return initial capital
    public double getInitialCapital(){ 
        return initialCapital;  
    }
}



// ----------- Market Class (Simulates daily/stress price changes) -------------
// Market stores stock prices and simulates random price movements
class Market {

    private final Map<String,Stock> stocks = new HashMap<>(); // stock list
    private final Random r = new Random();                     // randomizer

    // Constructor initializes market with real-like stock prices
    public Market(){
        stocks.put("AAPL", new TechStock("AAPL",185)); // Using inheritance
        stocks.put("GOOG", new TechStock("GOOG",135));
        stocks.put("TSLA", new Stock("TSLA",240));
        stocks.put("AMZN", new Stock("AMZN",145));
    }

    // Applies random price movement based on volatility level
    public synchronized void applyStress(double vol){

        // For each stock, apply new price using Gaussian movement
        for(Stock s : stocks.values()){

            double newPrice = 
                s.getPrice() * (1 + r.nextGaussian() * vol);

            // Ensure price can't fall below 0.01
            newPrice = Math.max(0.01, newPrice);

            // Round to 2 decimal places
            newPrice = Math.round(newPrice * 100) / 100.0;

            s.setPrice(newPrice); // update stock price
        }
    }

    // Getter to return a stock by symbol
    public Stock get(String sym){ 
        return stocks.get(sym); 
    }
}



// ----------- RiskModeler Class (Runs Monte Carlo risk simulation) -------------
// This class calculates the total portfolio value and finds the worst-case drawdown
class RiskModeler {

    private final Portfolio p;   // portfolio reference
    private final Market m;      // market reference

    // Constructor
    public RiskModeler(Portfolio p, Market m){ 
        this.p = p;  
        this.m = m;  
    }

    // Calculate full portfolio value using current market prices
    public double value(){

        return p.getHoldings()
                .entrySet()
                .stream()
                .mapToDouble(e -> 
                    m.get(e.getKey()).getPrice() * e.getValue()
                )
                .sum();
    }

    // Monte Carlo Simulation to find maximum drawdown
    public double monteCarlo(int sims, double vol){

        double initial = value(); // initial portfolio value
        double maxLoss = 0;       // maximum loss stored

        // Run multiple simulations
        for(int i = 0; i < sims; i++){

            // Create a *fresh* market for each simulation
            Market tempM = new Market();
            RiskModeler temp = new RiskModeler(p, tempM);

            double worst = 0; // worst loss inside this simulation

            // Simulate 10 stress days
            for(int d = 0; d < 10; d++){

                tempM.applyStress(vol);  // apply volatility

                double v = temp.value(); // new value

                worst = Math.max(worst, initial - v); // check loss
            }

            maxLoss = Math.max(maxLoss, worst); // update global max loss
        }

        // Return percentage drawdown
        return (maxLoss / initial) * 100;
    }
}



// ----------- Multithreading Class -------------
class SimulationThread extends Thread {

    private final Market market;
    private final double vol;

    public SimulationThread(Market m, double v) {
        this.market = m;
        this.vol = v;
    }

    @Override
    public void run() {
        market.applyStress(vol);
        System.out.println("[Thread] Stress applied with vol = " + vol);
    }
}



// ----------- Main Class (Driver Program) -------------
public class Main {

    public static void main(String[] args){

        try {
            // Create simulated DB manager
            DBManager db = new DBManager();
            db.connect();
            db.insertLog("Risk simulation started");

            // Create market
            Market m = new Market();

            // Create portfolio with fixed holdings
            Map<String,Integer> h = Map.of(
                "AAPL", 50,
                "GOOG", 10,
                "TSLA", 20
            );

            // Create portfolio object
            Portfolio p = new Portfolio(h);

            // Create risk model
            RiskModeler rm = new RiskModeler(p, m);

            // Create DAO and store a stock in it
            StockDAO<Stock> dao = new StockDAO<>();
            dao.save(new Stock("TEST", 100));

            // Multithreading example
            SimulationThread t1 = new SimulationThread(m, 0.02);
            t1.start();
            t1.join();

            // Calculate initial portfolio value
            double init = rm.value();
            System.out.println("Initial Value: $" + init);

            // Run Monte Carlo with normal volatility
            double normal = rm.monteCarlo(200, 0.02);
            System.out.println("Normal Max Drawdown: " + normal + "%");

            // Run Monte Carlo with high volatility
            double stress = rm.monteCarlo(200, 0.05);
            System.out.println("Stress Max Drawdown: " + stress + "%");

            // Print risk message
            System.out.println(stress > 15 ? "High Risk!" : "Risk Acceptable");

            db.insertLog("Simulation completed");
            db.disconnect();

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
