package routing;

import java.util.*;
import core.*;
import routing.RoutingDecisionEngine;

public class AntColonyRoutingProtocol implements RoutingDecisionEngine {

    protected Map<DTNHost, Map<DTNHost, Double>> pheromoneTable;
    protected Map<DTNHost, Double> heuristicTable;
    protected Map<Integer, List<DTNHost>> forwardAntPaths;
    protected Set<Integer> deliveredAnts;

    private static final double INITIAL_PHEROMONE = 0.1;
    private static final double GAMMA = 100;
    private static final double RHO = 0.2;
    private static final double ALPHA = 0.6;
    private static final double BETA = 0.4;
    private static final double M = 0.5;
    private static final double N = 0.5;

    private int sequenceNumber = 0;
    private Random rng = new Random();

    public AntColonyRoutingProtocol(Settings s) {
        pheromoneTable = new HashMap<>();
        heuristicTable = new HashMap<>();
        forwardAntPaths = new HashMap<>();
        deliveredAnts = new HashSet<>();
    }

    public AntColonyRoutingProtocol(AntColonyRoutingProtocol proto) {
        pheromoneTable = new HashMap<>();
        heuristicTable = new HashMap<>();
        forwardAntPaths = new HashMap<>();
        deliveredAnts = new HashSet<>();
    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        pheromoneTable.putIfAbsent(peer, new HashMap<>());
        for (DTNHost dest : pheromoneTable.keySet()) {
            pheromoneTable.get(dest).putIfAbsent(peer, INITIAL_PHEROMONE);
        }
        updateHeuristicInfo(peer);
    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        evaporatePheromones();
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DTNHost thisHost = con.getOtherNode(peer);
        updateHeuristicInfo(peer);

        if (thisHost.getRouter().getMessageCollection().size() > 0) {
            createForwardAnt(thisHost, peer);
        }
    }

    @Override
    public boolean newMessage(Message m) {
        return true;
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        return !thisHost.getRouter().hasMessage(m.getId());
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        DTNHost destination = m.getTo();
        if (otherHost == destination) {
            return true;
        }
        double probability = calculateForwardingProbability(destination, otherHost);
        return rng.nextDouble() < probability;
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        DTNHost destination = m.getTo();
        if (pheromoneTable.containsKey(destination)) {
            double thisPheromone = pheromoneTable.get(destination).getOrDefault(m.getFrom(), 0.0);
            double otherPheromone = pheromoneTable.get(destination).getOrDefault(otherHost, 0.0);
            return otherPheromone > thisPheromone;
        }
        return false;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return true;
    }
    @Override
    public void update(DTNHost thisHost) {
    } // Metode untuk memperbarui host

    @Override
    public RoutingDecisionEngine replicate() {
        return new AntColonyRoutingProtocol(this);
    }

    private void createForwardAnt(DTNHost source, DTNHost peer) {
        int antId = sequenceNumber++;
        List<DTNHost> path = new ArrayList<>();
        path.add(source);
        forwardAntPaths.put(antId, path);
        processForwardAnt(antId, peer, source);
    }

    private void processForwardAnt(int antId, DTNHost currentHost, DTNHost previousHost) {
        List<DTNHost> path = forwardAntPaths.get(antId);
        if (path == null || path.contains(currentHost)) return;

        path.add(currentHost);

        if (isFinalDest(null, currentHost)) {
            createBackwardAnt(antId, path);
            deliveredAnts.add(antId);
            return;
        }

        for (Connection conn : currentHost.getConnections()) {
            DTNHost neighbor = conn.getOtherNode(currentHost); // Ambil host yang terhubung
            if (!path.contains(neighbor)) {
                processForwardAnt(antId, neighbor, currentHost);
            }
        }
    }

    private void createBackwardAnt(int antId, List<DTNHost> path) {
        Collections.reverse(path);
        for (int i = 0; i < path.size() - 1; i++) {
            DTNHost from = path.get(i);
            DTNHost to = path.get(i + 1);

            pheromoneTable.putIfAbsent(from, new HashMap<>());
            double deltaTau = GAMMA * (1.0 / (path.size() - i));
            double currentTau = pheromoneTable.get(from).getOrDefault(to, INITIAL_PHEROMONE);
            pheromoneTable.get(from).put(to, currentTau + deltaTau);
        }
    }

    private void updateHeuristicInfo(DTNHost peer) {
        double connectivityChangeRate = 0.5;
        double load = 0.5;
        double heuristicValue = M * connectivityChangeRate + N * (1.0 / (load + 0.1));
        heuristicTable.put(peer, heuristicValue);
    }

    private double calculateForwardingProbability(DTNHost destination, DTNHost nextHop) {
        double pheromone = pheromoneTable.getOrDefault(destination, new HashMap<>())
                            .getOrDefault(nextHop, INITIAL_PHEROMONE);
        double heuristic = heuristicTable.getOrDefault(nextHop, 0.5);

        double numerator = ALPHA * pheromone + BETA * heuristic;
        double denominator = 0.0;

        if (pheromoneTable.containsKey(destination)) {
            for (DTNHost neighbor : pheromoneTable.get(destination).keySet()) {
                double neighborPheromone = pheromoneTable.get(destination).get(neighbor);
                double neighborHeuristic = heuristicTable.getOrDefault(neighbor, 0.5);
                denominator += ALPHA * neighborPheromone + BETA * neighborHeuristic;
            }
        }

        return denominator > 0 ? numerator / denominator : 0.0;
    }

    private void evaporatePheromones() {
        for (Map<DTNHost, Double> pheromones : pheromoneTable.values()) {
            for (DTNHost neighbor : new HashSet<>(pheromones.keySet())) {
                double newValue = pheromones.get(neighbor) * (1 - RHO);
                pheromones.put(neighbor, Math.max(newValue, INITIAL_PHEROMONE));
            }
        }
    }
}
