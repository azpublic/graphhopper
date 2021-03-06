/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.coll.LongIntMap;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.Helper;
import static com.graphhopper.util.Helper.*;
import java.io.*;
import javax.xml.stream.XMLStreamException;

import gnu.trove.list.TLongList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class parses an OSM xml file and creates a graph from it. See run.sh on
 * how to use it from command line.
 *
 * @author Peter Karich
 */
public class OSMReader {

    private static Logger logger = LoggerFactory.getLogger(OSMReader.class);
    private long locations;
    private long skippedLocations;
    private GraphStorage graphStorage;
    private OSMReaderHelper helper;
    private EncodingManager encodingManager = null;
    private int workerThreads = -1;

    public OSMReader(GraphStorage storage, long expectedNodes) {
        this.graphStorage = storage;
        helper = new OSMReaderHelper(graphStorage, expectedNodes);
    }

    public OSMReader workerThreads(int numOfWorkers) {
        this.workerThreads = numOfWorkers;
        return this;
    }

    public void osm2Graph(File osmFile) throws IOException {
        if (encodingManager == null)
            throw new IllegalStateException("Encoding manager not set.");

        long start = System.currentTimeMillis();
        preProcess(osmFile);

        long pass2 = System.currentTimeMillis();
        writeOsm2Graph(osmFile);

        final long finished = System.currentTimeMillis();
        logger.info("Times Pass1: " + (pass2 - start) + " Pass2: " + (finished - pass2) + " Total:" + (finished - start));
    }

    /**
     * Preprocessing of OSM file to select nodes which are used for highways.
     * This allows a more compact graph data structure.
     */
    public void preProcess(File osmFile) {
        try {
            OSMInputFile in = new OSMInputFile(osmFile).workerThreads(workerThreads).open();

            long tmpCounter = 1;

            OSMElement item;
            while ((item = in.getNext()) != null) {
                if (item.isType(OSMElement.WAY)) {
                    final OSMWay way = (OSMWay) item;
                    boolean valid = filterWay(way);
                    if (valid) {
                        TLongList wayNodes = way.nodes();
                        int s = wayNodes.size();
                        for (int index = 0; index < s; index++) {
                            helper.prepareHighwayNode(wayNodes.get(index));
                        }

                        if (++tmpCounter % 500000 == 0)
                            logger.info(nf(tmpCounter) + " (preprocess), osmIdMap:"
                                    + nf(helper.getNodeMap().size()) + " (" + helper.getNodeMap().memoryUsage() + "MB) "
                                    + Helper.memInfo());
                    }
                }
            }
            in.close();
        } catch (Exception ex) {
            throw new RuntimeException("Problem while parsing file", ex);
        }
    }

    /**
     * Filter ways but do not analyze properties wayNodes will be filled with
     * participating node ids.
     *
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay(OSMWay item) throws XMLStreamException {

        // ignore broken geometry
        if (item.nodes().size() < 2)
            return false;
        // ignore multipolygon geometry
        if (!item.hasTags())
            return false;

        return encodingManager.accept(item) > 0;
    }

    /**
     * Creates the edges and nodes files from the specified osm file.
     */
    void writeOsm2Graph(File osmFile) {

        int tmp = (int) Math.max(helper.foundNodes() / 50, 100);
        logger.info("creating graph. Found nodes (pillar+tower):" + nf(helper.foundNodes()) + ", " + Helper.memInfo());
        graphStorage.create(tmp);
        long wayStart = -1;
        long counter = 1;
        try {
            OSMInputFile in = new OSMInputFile(osmFile).workerThreads(workerThreads).open();
            LongIntMap nodeFilter = helper.getNodeMap();

            OSMElement item;
            while ((item = in.getNext()) != null) {
                switch (item.type()) {
                    case OSMElement.NODE:
                        if (nodeFilter.get(item.id()) != -1)
                            processNode((OSMNode) item);
                        break;

                    case OSMElement.WAY:
                        if (wayStart < 0) {
                            logger.info(nf(counter) + ", now parsing ways");
                            wayStart = counter;
                        }
                        processWay((OSMWay) item);
                        break;
                }
                if (++counter % 5000000 == 0) {
                    logger.info(nf(counter) + ", locs:" + nf(locations)
                            + " (" + skippedLocations + ") " + Helper.memInfo());
                }
            }
            in.close();
            // logger.info("storage nodes:" + storage.nodes() + " vs. graph nodes:" + storage.getGraph().nodes());
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't process file " + osmFile, ex);
        }
        helper.finishedReading();
        if (graphStorage.nodes() == 0)
            throw new IllegalStateException("osm must not be empty. read " + counter + " lines and " + locations + " locations");
    }

    /**
     * Process properties, encode flags and create edges for the way
     *
     *
     * @param way
     * @throws XMLStreamException
     */
    public void processWay(OSMWay way) throws XMLStreamException {
        if (way.nodes().size() < 2)
            return;
        // ignore multipolygon geometry
        if (!way.hasTags())
            return;

        int includeWay = encodingManager.accept(way);
        if (includeWay > 0) {
            int flags = encodingManager.encodeTags(includeWay, way);
            if (flags != 0)
                helper.addEdge(way.nodes(), flags);
        }
    }

    private void processNode(OSMNode node) throws XMLStreamException {

        if (isInBounds(node)) {
            helper.addNode(node);
            locations++;
        } else {
            skippedLocations++;
        }
    }

    /**
     * Filter method, override in subclass
     */
    boolean isInBounds(OSMNode node) {
        return true;
    }

    public GraphStorage graph() {
        return graphStorage;
    }

    /**
     * Specify the type of the path calculation (car, bike, ...).
     */
    public OSMReader encodingManager(EncodingManager acceptWay) {
        this.encodingManager = acceptWay;
        return this;
    }

    OSMReaderHelper helper() {
        return helper;
    }

    public void wayPointMaxDistance(double maxDist) {
        helper.wayPointMaxDistance(maxDist);
    }
}
