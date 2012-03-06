package uk.ac.ebi.fgpt.conan.process.atlas;

import net.sourceforge.fluxion.spi.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import uk.ac.ebi.arrayexpress2.magetab.datamodel.MAGETABInvestigation;
import uk.ac.ebi.arrayexpress2.magetab.datamodel.graph.Node;
import uk.ac.ebi.arrayexpress2.magetab.datamodel.sdrf.node.*;
import uk.ac.ebi.arrayexpress2.magetab.datamodel.sdrf.node.attribute.ArrayDesignAttribute;
import uk.ac.ebi.arrayexpress2.magetab.datamodel.sdrf.node.attribute.CharacteristicsAttribute;
import uk.ac.ebi.arrayexpress2.magetab.datamodel.sdrf.node.attribute.FactorValueAttribute;
import uk.ac.ebi.arrayexpress2.magetab.exception.ParseException;
import uk.ac.ebi.arrayexpress2.magetab.parser.MAGETABParser;
import uk.ac.ebi.fgpt.conan.ae.AccessionParameter;
import uk.ac.ebi.fgpt.conan.dao.DatabaseConanControlledVocabularyDAO;
import uk.ac.ebi.fgpt.conan.model.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.ConanProcess;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Process to check experiment eligibility for Atlas. Consists of six steps: 1
 * check for experiment type, 2 check for two-channel experiment, 3 check for
 * factor values, 4 check for factor types from controlled vocabulary only, 5
 * check for array design existence in Atlas, 6 check for raw data files for
 * Affy and derived data files for all other platforms.
 *
 * @author Natalja Kurbatova
 * @date 15/02/11
 */
@ServiceProvider
public class ExperimentEligibilityCheckingProcess implements ConanProcess {
    private final Collection<ConanParameter> parameters;
    private final AccessionParameter accessionParameter;
    private final List<String> ArrayDesignAccessions = new ArrayList<String>();
    private final DatabaseConanControlledVocabularyDAO controlledVocabularyDAO;

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    /**
     * Constructor for process. Initializes conan2 parameters for the process.
     */
    public ExperimentEligibilityCheckingProcess() {
        parameters = new ArrayList<ConanParameter>();
        accessionParameter = new AccessionParameter();
        parameters.add(accessionParameter);
        ClassPathXmlApplicationContext ctx =
                new ClassPathXmlApplicationContext("controlled-vocabulary-context.xml");
        controlledVocabularyDAO =
                ctx.getBean("databaseConanControlledVocabularyDAO",
                            DatabaseConanControlledVocabularyDAO.class);
    }

    public boolean execute(Map<ConanParameter, String> parameters)
            throws ProcessExecutionException, IllegalArgumentException,
            InterruptedException {

        int exitValue = 0;

        // Add to the desired logger
        BufferedWriter log;

        //errors and messaging
        String error_val = "";
        List<FailureReasons> failureReasons = new ArrayList<FailureReasons>();

        //deal with parameters
        final AccessionParameter accession = new AccessionParameter();
        accession.setAccession(parameters.get(accessionParameter));

        String reportsDir =
                accession.getFile().getParentFile().getAbsolutePath() + File.separator +
                        "reports";
        File reportsDirFile = new File(reportsDir);
        if (!reportsDirFile.exists()) {
            reportsDirFile.mkdirs();
        }

        String fileName = reportsDir + File.separator + accession.getAccession() +
                "_AtlasEligibilityCheck" +
                "_" + new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date()) +
                ".report";
        try {
            log = new BufferedWriter(new FileWriter(fileName));
            log.write("Atlas Eligibility Check: START\n");
        }
        catch (IOException e) {
            exitValue = 1;
            e.printStackTrace();

            ProcessExecutionException pex = new ProcessExecutionException(exitValue,
                                                                          "Can't create report file '" +
                                                                                  fileName +
                                                                                  "'");

            String[] errors = new String[1];
            errors[0] = "Can't create report file '" + fileName + "'";
            pex.setProcessOutput(errors);
            throw pex;
        }

        // make a new parser
        MAGETABParser parser = new MAGETABParser();

        try {
            MAGETABInvestigation investigation =
                    parser.parse(accession.getFile().getAbsoluteFile());

            // 1 check: experiment types
            boolean isAtlasType = false;
            String restrictedExptType = "";
            if (investigation.IDF.getComments().containsKey("AEExperimentType")) {
                for (String exptType : investigation.IDF.getComments()
                                                        .get("AEExperimentType")) {
                    for (String AtlasType : controlledVocabularyDAO
                            .getAtlasExperimentTypes()) {
                        if (exptType.equals(AtlasType)) {
                            isAtlasType = true;
                        }
                        else {
                            restrictedExptType = exptType;
                        }
                    }
                }
            }
            else {
                for (String exptType : investigation.IDF.experimentalDesign) {
                    for (String AtlasType : controlledVocabularyDAO
                            .getAtlasExperimentTypes()) {
                        if (exptType.equals(AtlasType)) {
                            isAtlasType = true;
                        }
                    }
                }
            }

            if (!isAtlasType)
            //not in Atlas Experiment Types
            {
                exitValue = 1;
                log.write(
                        "'Experiment Type' " + restrictedExptType +
                                " is not accepted by Atlas\n");
                getLog().debug(
                        "'Experiment Type' " + restrictedExptType +
                                " is not accepted by Atlas");
                error_val = "'Experiment Type' " + restrictedExptType +
                        " is not accepted by Atlas.\n";
                failureReasons.add(FailureReasons.TYPE_OF_EXPERIMENT);
            }

            //2 two-channel experiment
            if (investigation.SDRF.getNumberOfChannels() > 1) {
                exitValue = 1;
                log.write(
                        "Two-channel experiment is not accepted by Atlas\n");
                getLog().debug(
                        "Two-channel experiment is not accepted by Atlas");
                error_val = error_val + "Two-channel experiment is not accepted by Atlas. \n";
                failureReasons.add(FailureReasons.TWO_CHANNELS);
            }


            Collection<HybridizationNode> hybridizationNodes =
                    investigation.SDRF.getNodes(HybridizationNode.class);
            Collection<ArrayDataNode> rawDataNodes =
                    investigation.SDRF.getNodes(ArrayDataNode.class);
            Collection<DerivedArrayDataNode> processedDataNodes =
                    investigation.SDRF.getNodes(DerivedArrayDataNode.class);
            Collection<DerivedArrayDataMatrixNode> processedDataMatrixNodes =
                    investigation.SDRF.getNodes(DerivedArrayDataMatrixNode.class);
            int factorValues = 0;
            for (HybridizationNode hybNode : hybridizationNodes) {
                if (hybNode.factorValues.size() > 0) {
                    factorValues++;
                }

               
                ArrayDesignAccessions.clear();
                for (ArrayDesignAttribute arrayDesign : hybNode.arrayDesigns) {
                    if (!ArrayDesignAccessions
                            .contains(arrayDesign.getAttributeValue())) {
                        ArrayDesignAccessions.add(arrayDesign.getAttributeValue());
                    }
                }
            }


            boolean replicates = true;
            // All experiments must have replicates for at least 1 factor
            Hashtable<String,Integer> factorTypesCounts = new Hashtable<String, Integer>();
            for (HybridizationNode hybNode : hybridizationNodes) {
                    String arrayDesignName = "";
                    for (ArrayDesignAttribute arrayDesign : hybNode.arrayDesigns) {
                        arrayDesignName=arrayDesign.getAttributeValue() ;
                    }
                    for (FactorValueAttribute fva : hybNode.factorValues) {
                            String key = arrayDesignName+"_"+fva.type.toLowerCase()+"_" + fva.getAttributeValue();
                            System.out.println(key);
                            if (factorTypesCounts.get(key)==null){
                                factorTypesCounts.put(key,1);
                            }
                            else {
                                int value = factorTypesCounts.get(key);
                                value++;
                                factorTypesCounts.put(key,value);
                            }
                    }
            }

            for (Map.Entry<String, Integer> fvc : factorTypesCounts.entrySet()) {
                  if (fvc.getValue() == 1){
                    replicates = false;
                  }
            }

            // replicates
            if (replicates == false) {
                exitValue = 1;
                log.write(
                        "Experiment does not have replicates for at least 1 factor type\n");
                getLog().debug(
                        "Experiment does not have replicates for at least 1 factor type");
                error_val = error_val + "Experiment does not have replicates for at least 1 factor type. \n";
                failureReasons.add(FailureReasons.REPLICATES);
            }

            //3 factor values
            if (factorValues == 0) {
                exitValue = 1;
                log.write(
                        "Experiment does not have Factor Values\n");
                getLog().debug(
                        "Experiment does not have Factor Values");
                error_val = error_val + "Experiment does not have Factor Values. \n";
                failureReasons.add(FailureReasons.NO_FACTOR_VALUES);
            }

            //6 and 7 factor types are from controlled vocabulary and not repeated
            //6 and 7 factor types are from controlled vocabulary and not repeated
            boolean factorTypesFromCV = true;
            boolean factorTypesVariable = true;
            boolean characteristicsFromCV = true;
            boolean characteristicsVariable = true;
            List<String> missedFactorTypes = new ArrayList<String>();
            List<String> missedCharacteristics = new ArrayList<String>();
            List<String> repeatedFactorTypes = new ArrayList<String>();
            List<String> repeatedFactorTypesList = new ArrayList<String>();
            List<String> repeatedCharacteristics = new ArrayList<String>();

            for (String factorType : investigation.IDF.experimentalFactorType) {
                if (!controlledVocabularyDAO
                        .getAtlasFactorTypes().contains(factorType.toLowerCase())) {
                    factorTypesFromCV = false;
                    missedFactorTypes.add(factorType);
                }
                if (repeatedFactorTypes.contains(factorType)) {
                    factorTypesVariable = false;
                    if (repeatedFactorTypesList.contains(factorType)){
                        repeatedFactorTypesList.add(factorType);
                    }
                }
                repeatedFactorTypes.add(factorType);
            }
            for (SampleNode sampleNode : investigation.SDRF.getNodes(SampleNode.class)) {
                for (CharacteristicsAttribute ca : sampleNode.characteristics) {
                    String key = sampleNode.getNodeName() + ca.type;
                    if (ca.type.toLowerCase().equals("organism")) {
                        if (repeatedCharacteristics.contains(key)) {
                            characteristicsVariable = false;
                        }
                        repeatedCharacteristics.add(key);
                    }

                    if (!controlledVocabularyDAO
                            .getAtlasFactorTypes().contains(ca.type.toLowerCase())) {
                        characteristicsFromCV = false;
                        if (!missedCharacteristics.contains(ca.type)){
                            missedCharacteristics.add(ca.type);
                        }
                    }
                }
            }
            for (SourceNode sourceNode : investigation.SDRF.getNodes(SourceNode.class)) {
                for (CharacteristicsAttribute ca : sourceNode.characteristics) {
                    String key = sourceNode.getNodeName() + ca.type;
                    if (ca.type.toLowerCase().equals("organism")) {
                        if (repeatedCharacteristics.contains(key)) {
                            characteristicsVariable = false;
                        }
                        repeatedCharacteristics.add(key);
                    }

                    if (!controlledVocabularyDAO
                            .getAtlasFactorTypes().contains(ca.type.toLowerCase())) {
                        characteristicsFromCV = false;
                        if (!missedCharacteristics.contains(ca.type)){
                            missedCharacteristics.add(ca.type);
                        }
                    }
                }
            }
            if (!factorTypesFromCV) {
                exitValue = 1;
                log.write(
                        "Experiment has Factor Types that are not in controlled vocabulary:" +
                                missedFactorTypes + "\n");
                getLog().debug(
                        "Experiment has Factor Types that are not in controlled vocabulary:" +
                                missedFactorTypes);
                error_val = error_val +
                        "Experiment has Factor Types that are not in controlled vocabulary:" +
                        missedFactorTypes + ".\n";
                failureReasons.add(FailureReasons.NOT_IN_CONTROLLED_VOCABULARY);
            }
            if (!characteristicsFromCV) {
                exitValue = 1;
                log.write(
                        "Experiment has Characteristics that are not in controlled vocabulary:" +
                                missedCharacteristics + "\n");
                getLog().debug(
                        "Experiment has Characteristics that are not in controlled vocabulary:" +
                                missedCharacteristics);
                error_val = error_val +
                        "Experiment has Characteristics that are not in controlled vocabulary:" +
                        missedCharacteristics + ".\n";
                if (!failureReasons.contains(FailureReasons.NOT_IN_CONTROLLED_VOCABULARY)){
                    failureReasons.add(FailureReasons.NOT_IN_CONTROLLED_VOCABULARY);    
                }
            }

            if (!factorTypesVariable) {
                exitValue = 1;
                log.write("Experiment has repeated Factor Types: " +
                        repeatedFactorTypesList + "\n");
                getLog().debug("Experiment has repeated Factor Types: " +
                        repeatedFactorTypesList + ".");
                error_val = error_val + "Experiment has repeated Factor Types: " +
                        repeatedFactorTypesList + ".\n";
                failureReasons.add(FailureReasons.REPEATED);
            }

            if (!characteristicsVariable) {
                exitValue = 1;
                log.write("Experiment has repeated Characteristics \"Organism\" .\n");
                getLog().debug("Experiment has repeated Characteristics \"Organism\" .");
                error_val = error_val + "Experiment has repeated Characteristics \"Organism\".\n";
                if (!failureReasons.contains(FailureReasons.REPEATED)){
                    failureReasons.add(FailureReasons.REPEATED);
                }
            }

            // 5 check: array design is in Atlas
            for (String arrayDesign : ArrayDesignAccessions) {
                ArrayDesignExistenceChecking arrayDesignExistenceChecking =
                        new ArrayDesignExistenceChecking();
                String arrayCheckResult =
                        arrayDesignExistenceChecking.execute(arrayDesign);
                if (arrayCheckResult.equals("empty") ||
                        arrayCheckResult.equals("no")) {
                    exitValue = 1;
                    log.write("Array design '" +
                                      arrayDesign +
                                      "' used in experiment is not in Atlas\n");
                    getLog().debug("Array design '" +
                                           arrayDesign +
                                           "' used in experiment is not in Atlas");
                    error_val = error_val + "Array design '" +
                            arrayDesign +
                            "' used in experiment is not in Atlas. \n";
                    failureReasons.add(FailureReasons.ARRAY_DESIGN_NOT_IN_ATLAS);
                }

                else {
                    //Array Design is in Atlas
                    Collection<HybridizationNode> hybridizationSubNodes =
                            new ArrayList<HybridizationNode>();
                    Collection<Node> rawDataSubNodes = new ArrayList<Node>();
                    Collection<Node> processedDataSubNodes = new ArrayList<Node>();
                    Collection<Node> processedDataMatrixSubNodes =
                            new ArrayList<Node>();
                    //Number of arrays in experiment
                    if (ArrayDesignAccessions.size() > 1) {

                        for (HybridizationNode hybNode : hybridizationNodes) {
                            ArrayDesignAttribute attribute = new ArrayDesignAttribute();
                            attribute.setAttributeValue(arrayDesign);

                            if (hybNode.arrayDesigns.contains(attribute)) {
                                //get data nodes for particular array design
                                hybridizationSubNodes.add(hybNode);
                                getNodes(hybNode, ArrayDataNode.class, rawDataSubNodes);
                                getNodes(hybNode, DerivedArrayDataNode.class,
                                         processedDataSubNodes);
                                getNodes(hybNode, DerivedArrayDataMatrixNode.class,
                                         processedDataMatrixSubNodes);

                            }

                        }
                    }
                    else {
                        //one array design in experiment
                        hybridizationSubNodes = hybridizationNodes;
                        for (ArrayDataNode node : rawDataNodes) {
                            rawDataSubNodes.add(node);
                        }
                        for (DerivedArrayDataNode node : processedDataNodes) {
                            processedDataSubNodes.add(node);
                        }
                        for (DerivedArrayDataMatrixNode node : processedDataMatrixNodes) {
                            processedDataMatrixSubNodes.add(node);
                        }

                    }

                    //6 check: if Affy then check for raw data files, else for derived
                    if (arrayCheckResult.equals("affy") &&
                            hybridizationSubNodes.size() != rawDataSubNodes.size()) {   //6a affy
                        exitValue = 1;
                        if (rawDataSubNodes.size()==0) {
                            log.write(
                                    "Affymetrix experiment without raw data files\n");
                            getLog().debug(
                                    "Affymetrix experiment without raw data files");
                            error_val = error_val + "Affymetrix experiment without raw data files.\n";
                        }
                        else {
                            log.write(
                                    "Affymetrix experiment with different numbers of hybs ("
                                    +hybridizationSubNodes.size()+") and raw data files ("+rawDataSubNodes.size()+") \n");
                            getLog().debug(
                                    "Affymetrix experiment with different numbers of hybs ("
                                    +hybridizationSubNodes.size()+") and raw data files ("+rawDataSubNodes.size()+")");
                            error_val = error_val + "Affymetrix experiment with different numbers of hybs ("
                                    +hybridizationSubNodes.size()+") and raw data files ("+rawDataSubNodes.size()+").\n";
                        }
                        failureReasons.add(FailureReasons.DATA_FILES_MISSING);
                    }
                    else {
                        //6b not affy without processed data
                        if (!arrayCheckResult.equals("affy") &&
                                //processedDataSubNodes.size() == 0 &&
                                processedDataMatrixSubNodes.size() == 0) {
                            exitValue = 1;
                            log.write(
                                    "Non-Affymetrix experiment without processed data files\n");
                            getLog().debug(
                                    "Non-Affymetrix experiment without processed data files");
                            error_val = error_val +
                                    "Non-Affymetrix experiment without processed data files. \n";
                            failureReasons.add(FailureReasons.DATA_FILES_MISSING);

                        }
                    }
                }
            }

            //error message for Submission Tracking database
            String message = "";
            Collections.sort(failureReasons);
            for (FailureReasons reason : failureReasons){
                message = message + reason.getCode() + ",";
            }

            if (message.length() > 1){
                message = message.substring(0,message.length()-1);
            }

            if (exitValue == 1) {
                ProcessExecutionException pex = new ProcessExecutionException(exitValue,
                                                                              error_val, message);
                String[] errors = new String[1];
                errors[0] = error_val;
                pex.setProcessOutput(errors);
                pex.setExceptionCausesAbort();
                throw pex;
            }

        }
        catch (ParseException e) {
            exitValue = 1;
            ProcessExecutionException pex = new ProcessExecutionException(exitValue,
                                                                          e.getMessage());

            String[] errors = new String[1];
            errors[0] = e.getMessage();
            errors[1] =
                    "Please check MAGE-TAB files and/or run validation process.\n";
            pex.setProcessOutput(errors);
            throw pex;
        }
        catch (IOException e) {
            exitValue = 1;
            ProcessExecutionException pex = new ProcessExecutionException(exitValue,
                                                                          e.getMessage());

            String[] errors = new String[1];
            errors[0] = e.getMessage();
            pex.setProcessOutput(errors);
            throw pex;
        }
//        catch (RuntimeException e) {
//            //e.printStackTrace();
//            exitValue = 1;
//            ProcessExecutionException pex = new ProcessExecutionException(exitValue,
//                                                                          e.getMessage());
//
//            String[] errors = new String[1];
//            errors[0] = e.getMessage();
//            pex.setProcessOutput(errors);
//            throw pex;
//        }
        finally {
            try {
                if (exitValue == 0) {
                    log.write("Experiment \"" +
                                      accession.getAccession() +
                                      "\" is eligible for Atlas\n");
                }
                else {
                    log.write("Experiment \"" +
                                      accession.getAccession() +
                                      "\" is NOT eligible for Atlas\n");

                }
                log.write("Atlas Eligibility Check: FINISHED\n");
                log.write(
                        "Eligibility checks for Gene Expression Atlas version 2.0.9.3: \n" +
                                "1. Experiment has raw data for Affymetrix platforms or normalized data for all other platforms;\n" +
                                "2. Array design(s) used in experiment are loaded into Atlas;\n" +
                                "3. Type of experiment is from the list: \n" +
                                " - transcription profiling by array,\n" +
                                " - methylation profiling by array,\n" +
                                " - tiling path by array,\n" +
                                " - comparative genomic hybridization by array,\n" +
                                " - microRNA profiling by array,\n" +
                                " - RNAi profiling by array,\n" +
                                " - ChIP-chip by array;\n" +
                                "4. Experiment is not two-channel;\n" +
                                "5. Experiment has factor values;\n" +
                                "6. Experiment has replicates for at least 1 factor type;\n"+
                                "7. Factor types and Characteristics types are from controlled vocabulary;\n" +
                                "8. Factor types and Characteristics types are variable (not repeated).");
                log.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                ProcessExecutionException pex = new ProcessExecutionException(exitValue,
                                                                              e.getMessage());

                String[] errors = new String[1];
                errors[0] = e.getMessage();
                pex.setProcessOutput(errors);
                throw pex;
            }
        }

        ProcessExecutionException pex = new ProcessExecutionException(exitValue,
                                                                      "Something wrong in the code ");
        if (exitValue == 0) {
            return true;
        }
        else {
            String[] errors = new String[1];
            errors[0] = "Something wrong in the code ";
            pex.setProcessOutput(errors);
            throw pex;
        }
    }


    public boolean executeMockup(String file)
            throws ProcessExecutionException, IllegalArgumentException {

        // Add to the desired logger
        BufferedWriter log;
        List<FailureReasons> fr = new ArrayList<FailureReasons>();

        boolean result = false;

        // now, parse from a file
        File idfFile = new File(file);
        int exitValue = 0;
        String error_val = "";

        String reportsDir =
                idfFile.getParentFile().getAbsolutePath() + File.separator +
                        "reports";
        File reportsDirFile = new File(reportsDir);
        if (!reportsDirFile.exists()) {
            reportsDirFile.mkdirs();
        }

        String fileName = reportsDir + File.separator + idfFile.getName() +
                "_AtlasEligibilityCheck" +
                "_" + new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date()) +
                ".report";
        try {
            log = new BufferedWriter(new FileWriter(fileName));
            log.write("Atlas Eligibility Check: START\n");
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new ProcessExecutionException(1, "Can't create report file '" +
                    fileName + "'", e);
        }

        // make a new parser
        MAGETABParser parser = new MAGETABParser();


        try {
            MAGETABInvestigation investigation = parser.parse(idfFile);
            // I check: experiment types
            boolean isAtlasType = true;

            String restrictedExptType = "";
           /* if (investigation.IDF.getComments().containsKey("AEExperimentType")) {
                for (String exptType : investigation.IDF.getComments()
                        .get("AEExperimentType")) {
                    for (String AtlasType : controlledVocabularyDAO
                            .getAtlasExperimentTypes()) {
                        if (exptType.equals(AtlasType)) {
                            isAtlasType = true;
                        }
                        else {
                            restrictedExptType = exptType;
                        }
                    }
                }
            }
            else {
                for (String exptType : investigation.IDF.experimentalDesign) {
                    for (String AtlasType : controlledVocabularyDAO
                            .getAtlasExperimentTypes()) {
                        if (exptType.equals(AtlasType)) {
                            isAtlasType = true;
                        }
                    }
                }
            }        */

            if (!isAtlasType)
            //not in Atlas Experiment Types
            {
                exitValue = 1;
                log.write(
                        "'Experiment Type' " + restrictedExptType +
                                " is not accepted by Atlas\n");
                getLog().debug(
                        "'Experiment Type' " + restrictedExptType +
                                " is not accepted by Atlas");
                error_val = "'Experiment Type' " + restrictedExptType +
                        " is not accepted by Atlas.\n";
                fr.add(FailureReasons.TYPE_OF_EXPERIMENT);
            }

            //2 two-channel experiment
            if (investigation.SDRF.getNumberOfChannels() > 1) {
                exitValue = 1;
                log.write(
                        "Two-channel experiment is not accepted by Atlas\n");
                getLog().debug(
                        "Two-channel experiment is not accepted by Atlas");
                error_val = error_val + "Two-channel experiment is not accepted by Atlas. \n";
                fr.add(FailureReasons.TWO_CHANNELS);
            }


            Collection<HybridizationNode> hybridizationNodes =
                    investigation.SDRF.getNodes(HybridizationNode.class);
            Collection<ArrayDataNode> rawDataNodes =
                    investigation.SDRF.getNodes(ArrayDataNode.class);
            Collection<DerivedArrayDataNode> processedDataNodes =
                    investigation.SDRF.getNodes(DerivedArrayDataNode.class);
            Collection<DerivedArrayDataMatrixNode> processedDataMatrixNodes =
                    investigation.SDRF.getNodes(DerivedArrayDataMatrixNode.class);
            int factorValues = 0;
            for (HybridizationNode hybNode : hybridizationNodes) {
                if (hybNode.factorValues.size() > 0) {
                    factorValues++;
                }


                ArrayDesignAccessions.clear();
                for (ArrayDesignAttribute arrayDesign : hybNode.arrayDesigns) {
                    if (!ArrayDesignAccessions
                            .contains(arrayDesign.getAttributeValue())) {
                        ArrayDesignAccessions.add(arrayDesign.getAttributeValue());
                    }
                }
            }


            boolean replicates = true;
            // All experiments must have replicates for at least 1 factor
            Hashtable<String,Integer> factorTypesCounts = new Hashtable<String, Integer>();
            for (String factorType : investigation.IDF.experimentalFactorName) {
                //Hashtable<String,Integer> factorValuesCounts = new Hashtable<String, Integer>();
                for (HybridizationNode hybNode : hybridizationNodes) {
                    String arrayDesignName = "";
                    for (ArrayDesignAttribute arrayDesign : hybNode.arrayDesigns) {
                        arrayDesignName=arrayDesign.getAttributeValue() ;
                    }
                    for (FactorValueAttribute fva : hybNode.factorValues) {
                        //System.out.println(fva.type.toLowerCase()+" - "+factorType.toLowerCase());
                       // if (fva.type.toLowerCase().equals(factorType.toLowerCase())) {
                            String key = arrayDesignName+"_"+fva.type.toLowerCase()+"_" + fva.getAttributeValue();
                            System.out.println(key);
                            if (factorTypesCounts.get(key)==null){
                                factorTypesCounts.put(key,1);
                            }
                            else {
                                int value = factorTypesCounts.get(key);
                                value++;
                                factorTypesCounts.put(key,value);
                            }
                       // }
                    }
                }

               // factorTypesCounts.put(factorType,factorValuesCounts);
            }

            for (Map.Entry<String, Integer> fvc : factorTypesCounts.entrySet()) {
                  if (fvc.getValue() == 1){
                    replicates = false;
                    System.out.println(fvc.getKey() + "-" +fvc.getValue());
                  }
            }

          /*  for (Hashtable<String,Integer> fvc : factorTypesCounts.values() ) {
                for (int val : fvc.values()){
                    if (val == 1){
                        replicates = false;
                    }
                }
            }*/

            // replicates
            if (replicates == false) {
                exitValue = 1;
                log.write(
                        "Experiment does not have replicates for at least 1 factor type\n");
                getLog().debug(
                        "Experiment does not have replicates for at least 1 factor type");
                error_val = error_val + "Experiment does not have replicates for at least 1 factor type. \n";
                fr.add(FailureReasons.REPLICATES);
            }

            //3 factor values
            if (factorValues == 0) {
                exitValue = 1;
                log.write(
                        "Experiment does not have Factor Values\n");
                getLog().debug(
                        "Experiment does not have Factor Values");
                error_val = error_val + "Experiment does not have Factor Values. \n";
                fr.add(FailureReasons.NO_FACTOR_VALUES);
            }

            //6 and 7 factor types are from controlled vocabulary and not repeated
            boolean factorTypesFromCV = true;
            boolean factorTypesVariable = true;
            boolean characteristicsFromCV = true;
            boolean characteristicsVariable = true;
            List<String> missedFactorTypes = new ArrayList<String>();
            List<String> missedCharacteristics = new ArrayList<String>();
            List<String> repeatedFactorTypes = new ArrayList<String>();
            List<String> repeatedFactorTypesList = new ArrayList<String>();
            List<String> repeatedCharacteristics = new ArrayList<String>();
            List<String> repeatedCharacteristicsList = new ArrayList<String>();
            for (String factorType : investigation.IDF.experimentalFactorType) {
                //System.out.println("Repeated1");
                if (repeatedFactorTypes.contains(factorType)) {
                    factorTypesVariable = false;
                    if (repeatedFactorTypesList.contains(factorType)){
                        repeatedFactorTypesList.add(factorType);
                    }
                }
                repeatedFactorTypes.add(factorType);
            }
            for (SampleNode sampleNode : investigation.SDRF.getNodes(SampleNode.class)) {
                //System.out.println("Repeated2");
                for (CharacteristicsAttribute ca : sampleNode.characteristics) {
                    String key = sampleNode.getNodeName() + ca.type;
                    //System.out.println("Key:" + key );
                    if (repeatedCharacteristics.contains(key)) {
                        characteristicsVariable = false;
                        if (!repeatedCharacteristicsList.contains(ca.type)) {
                            repeatedCharacteristicsList.add(ca.type);
                        }
                    }
                    repeatedCharacteristics.add(key);

                }
            }
            for (SourceNode sourceNode : investigation.SDRF.getNodes(SourceNode.class)) {
                //System.out.println("Repeated2");
                for (CharacteristicsAttribute ca : sourceNode.characteristics) {
                    String key = sourceNode.getNodeName() + ca.type;
                    //System.out.println("Key:" + key );
                    if (repeatedCharacteristics.contains(key)) {
                        characteristicsVariable = false;
                        if (!repeatedCharacteristicsList.contains(ca.type)) {
                            repeatedCharacteristicsList.add(ca.type);
                        }
                    }
                    repeatedCharacteristics.add(key);

                }
            }
            if (!factorTypesFromCV) {
                exitValue = 1;
                log.write(
                        "Experiment has Factor Types that are not in controlled vocabulary:" +
                                missedFactorTypes + "\n");
                getLog().debug(
                        "Experiment has Factor Types that are not in controlled vocabulary:" +
                                missedFactorTypes);
                error_val = error_val +
                        "Experiment has Factor Types that are not in controlled vocabulary:" +
                        missedFactorTypes + ".\n";
                fr.add(FailureReasons.NOT_IN_CONTROLLED_VOCABULARY);
            }
            if (!characteristicsFromCV) {
                exitValue = 1;
                log.write(
                        "Experiment has Characteristics that are not in controlled vocabulary:" +
                                missedCharacteristics + "\n");
                getLog().debug(
                        "Experiment has Characteristics that are not in controlled vocabulary:" +
                                missedCharacteristics + ".");
                error_val = error_val +
                        "Experiment has Characteristics that are not in controlled vocabulary:" +
                        missedCharacteristics + ".\n";
                if (!fr.contains(FailureReasons.NOT_IN_CONTROLLED_VOCABULARY)){
                    fr.add(FailureReasons.NOT_IN_CONTROLLED_VOCABULARY);
                }
            }

            if (!factorTypesVariable) {
                exitValue = 1;
                log.write("Experiment has repeated Factor Types: " +
                        repeatedFactorTypesList + "\n");
                getLog().debug("Experiment has repeated Factor Types: " +
                        repeatedFactorTypesList + ".");
                error_val = error_val + "Experiment has repeated Factor Types: " +
                        repeatedFactorTypesList + ".\n";
                fr.add(FailureReasons.REPEATED);
            }

            if (!characteristicsVariable) {
                exitValue = 1;
                log.write("Experiment has repeated Characteristics: " +
                        repeatedCharacteristicsList + ".\n");
                getLog().debug("Experiment has repeated Characteristics: " +
                        repeatedCharacteristicsList + ".");
                error_val = error_val + "Experiment has repeated Characteristics: " +
                        repeatedCharacteristicsList + ".\n";
                if (!fr.contains(FailureReasons.REPEATED)){
                    fr.add(FailureReasons.REPEATED);
                }
            }

            // 5 check: array design is in Atlas
            for (String arrayDesign : ArrayDesignAccessions) {
                ArrayDesignExistenceChecking arrayDesignExistenceChecking =
                        new ArrayDesignExistenceChecking();
                String arrayCheckResult =
                        arrayDesignExistenceChecking.execute(arrayDesign);
                if (arrayCheckResult.equals("empty") ||
                        arrayCheckResult.equals("no")) {
                    exitValue = 1;
                    log.write("Array design '" +
                            arrayDesign +
                            "' used in experiment is not in Atlas\n");
                    getLog().debug("Array design '" +
                            arrayDesign +
                            "' used in experiment is not in Atlas");
                    error_val = error_val + "Array design '" +
                            arrayDesign +
                            "' used in experiment is not in Atlas. \n";
                    fr.add(FailureReasons.ARRAY_DESIGN_NOT_IN_ATLAS);
                }

                else {
                    //Array Design is in Atlas
                    Collection<HybridizationNode> hybridizationSubNodes =
                            new ArrayList<HybridizationNode>();
                    Collection<Node> rawDataSubNodes = new ArrayList<Node>();
                    Collection<Node> processedDataSubNodes = new ArrayList<Node>();
                    Collection<Node> processedDataMatrixSubNodes =
                            new ArrayList<Node>();
                    //Number of arrays in experiment
                    if (ArrayDesignAccessions.size() > 1) {

                        for (HybridizationNode hybNode : hybridizationNodes) {
                            ArrayDesignAttribute attribute = new ArrayDesignAttribute();
                            attribute.setAttributeValue(arrayDesign);

                            if (hybNode.arrayDesigns.contains(attribute)) {
                                //get data nodes for particular array design
                                hybridizationSubNodes.add(hybNode);
                                getNodes(hybNode, ArrayDataNode.class, rawDataSubNodes);
                                getNodes(hybNode, DerivedArrayDataNode.class,
                                        processedDataSubNodes);
                                getNodes(hybNode, DerivedArrayDataMatrixNode.class,
                                        processedDataMatrixSubNodes);

                            }

                        }
                    }
                    else {
                        //one array design in experiment
                        hybridizationSubNodes = hybridizationNodes;
                        for (ArrayDataNode node : rawDataNodes) {
                            rawDataSubNodes.add(node);
                        }
                        for (DerivedArrayDataNode node : processedDataNodes) {
                            processedDataSubNodes.add(node);
                        }
                        for (DerivedArrayDataMatrixNode node : processedDataMatrixNodes) {
                            processedDataMatrixSubNodes.add(node);
                        }

                    }

                    //6 check: if Affy then check for raw data files, else for derived
                    if (arrayCheckResult.equals("affy") &&
                            hybridizationSubNodes.size() != rawDataSubNodes.size()) { 
                        exitValue = 1;
                        if (rawDataSubNodes.size()==0) {
                            log.write(
                                "Affymetrix experiment without raw data files\n");
                            getLog().debug(
                                "Affymetrix experiment without raw data files");
                            error_val = error_val + "Affymetrix experiment without raw data files.\n";
                        }
                        else {
                            log.write(
                                    "Affymetrix experiment with different numbers of hybs ("+hybridizationSubNodes.size()+") and raw data files ("+rawDataSubNodes.size()+") \n");
                            getLog().debug(
                                    "Affymetrix experiment with different numbers of hybs ("+hybridizationSubNodes.size()+") and raw data files ("+rawDataSubNodes.size()+")");
                            error_val = error_val + "Affymetrix experiment with different numbers of hybs ("+hybridizationSubNodes.size()+") and raw data files ("+rawDataSubNodes.size()+").\n";
                        }
                        fr.add(FailureReasons.DATA_FILES_MISSING);
                    }
                    else {
                        //6b not affy without processed data
                        if (!arrayCheckResult.equals("affy") &&
                                //processedDataSubNodes.size() == 0 &&
                                processedDataMatrixSubNodes.size() == 0) {
                            exitValue = 1;
                            log.write(
                                    "Non-Affymetrix experiment without processed data files\n");
                            getLog().debug(
                                    "Non-Affymetrix experiment without processed data files");
                            error_val = error_val +
                                    "Non-Affymetrix experiment without processed data files. \n";
                            fr.add(FailureReasons.DATA_FILES_MISSING);

                        }
                    }
                }
            }
        }
        catch (Exception e) {
            result = false;
            e.printStackTrace();
            throw new ProcessExecutionException(1,
                                                "Atlas Eligibility Check: something is wrong in the code",
                                                e);
        }
        finally {
            try {
                if (result) {
                    log.write(
                            "Atlas Eligibility Check: experiment \"" + idfFile.getName() +
                                    "\" is eligible for ArrayExpress.\n");
                }
                else {
                    log.write(
                            "Atlas Eligibility Check: experiment \"" + idfFile.getName() +
                                    "\" is NOT eligible for ArrayExpress.\n");
                }
                log.write(error_val);
                System.out.println(error_val);
                String message = "";
                Collections.sort(fr);
                for (FailureReasons reason : fr){
                    message = message + reason.getCode() + ",";
                }
                System.out.println(message.substring(0,message.length()-1));

                log.write("Atlas Eligibility Check: FINISHED\n");
                log.write(
                        "Eligibility checks for Gene Expression Atlas version 2.0.9.3: \n" +
                                "1. Experiment has raw data for Affymetrix platforms or normalized data for all other platforms;\n" +
                                "2. Array design(s) used in experiment are loaded into Atlas;\n" +
                                "3. Type of experiment: transcription profiling by array,\n" +
                                "methylation profiling by array,\n" +
                                "tiling path by array,\n" +
                                "comparative genomic hybridization by array,\n" +
                                "microRNA profiling by array,\n" +
                                "RNAi profiling by array,\n" +
                                "ChIP-chip by array;\n" +
                                "4. Two-channel experiments - can't be loaded into Atlas;\n" +
                                "5. Experiment has factor values;\n" +
                                "6. Factor types are from controlled vocabulary;\n" +
                                "7. Factor types are variable (not repeated).");
                log.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                throw new ProcessExecutionException(1,
                                                    "Atlas Eligibility Check: can't close report file",
                                                    e);
            }
        }

        return result;
    }

    private Collection<Node> getNodes(Node parentNode, Class typeOfNode,
                                      Collection<Node> nodes) {
        for (Node childNode : parentNode.getChildNodes()) {
            if (childNode.getClass().equals(typeOfNode) &&
                    !nodes.contains(childNode)) {
                nodes.add(childNode);
            }
            else {
                getNodes(childNode, typeOfNode, nodes);
            }
        }
        return nodes;
    }

    /**
     * Returns the name of this process.
     *
     * @return the name of this process
     */
    public String getName() {
        return "atlas eligibility";
    }

    /**
     * Returns a collection of strings representing the names of the parameters.
     *
     * @return the parameter names required to generate a task
     */
    public Collection<ConanParameter> getParameters() {
        return parameters;
    }


}
