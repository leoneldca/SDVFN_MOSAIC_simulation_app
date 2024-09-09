package org.eclipse.mosaic.app.sdnvfn.network;

import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.graph.DefaultGraphIterables;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;



/**
 * A classe NetworkTopology implementa a representação da rede em um grado direcionado por meio da library JGraphT
 *
 */
public class NetworkTopology {

    private DefaultDirectedWeightedGraph<NetworkNode, DefaultWeightedEdge> networkGraph;

    public NetworkTopology(ArrayList<String> adjList, HashMap<String, MutableGeoPoint> rsuPositionsMap) {
        networkGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.populateNetGraph(adjList, rsuPositionsMap);

    }

    private NetworkNode createNetNode(String nodeId,MutableGeoPoint rsuGeoPoint){
        return new NetworkNode(nodeId,rsuGeoPoint);
    }
    private void populateNetGraph(ArrayList<String> adjArrayList, HashMap<String, MutableGeoPoint> rsuPositionsMap){
        String[] pairRsuConnections;
        String[] connectedRSUs;
        for (String rsuAdjStr: adjArrayList) {
            pairRsuConnections = rsuAdjStr.split(":");
            networkGraph.addVertex(createNetNode(pairRsuConnections[0],rsuPositionsMap.get(pairRsuConnections[0])));
        }
        for (String rsuConn: adjArrayList) {
            pairRsuConnections = rsuConn.split(":");
            connectedRSUs = pairRsuConnections[1].split(",");
            for (int i=0;i<connectedRSUs.length;i++) {
                 //System.out.println(getNetNode(pairRsuConnections[0]).toString()+"-->"+getNetNode(connectedRSUs[i]).toString());
                 networkGraph.addEdge(getNetNode(pairRsuConnections[0]),getNetNode(connectedRSUs[i]));

            }
        }
    }

    public GraphPath<NetworkNode,DefaultWeightedEdge> getPathRsuToRsu(NetworkNode sourceRsu, NetworkNode targetRsu ){
        DijkstraShortestPath<NetworkNode,DefaultWeightedEdge> shortestPathAlgorithm = new DijkstraShortestPath<>((Graph<NetworkNode, DefaultWeightedEdge>) this.networkGraph);
        //System.out.println("source:"+sourceRsu.getRsuId()+"-----"+"Target:"+targetRsu.getRsuId());
        return shortestPathAlgorithm.getPath(sourceRsu,targetRsu);
    }
    public NetworkNode getNetNode(String rsuId){
        DepthFirstIterator  <NetworkNode, DefaultWeightedEdge> graphIterator = new DepthFirstIterator<>(networkGraph);
        while (graphIterator.hasNext()) {
            NetworkNode currentNetNode = graphIterator.next();
            if (currentNetNode.getRsuId().equals(rsuId)) {
                return currentNetNode;
            }
        }
        return null;
    }








}
