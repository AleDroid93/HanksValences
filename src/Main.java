import edu.mit.jwi.Dictionary;
import edu.mit.jwi.item.*;
import edu.smu.tspell.wordnet.*;
import edu.smu.tspell.wordnet.Synset;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.mit.jwi.*;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import java.io.*;
import java.net.URL;
import java.util.*;

public class Main {

    static String contextPath = "[PATH_TO_CONTEXT_INPUT_FILES]";
    static String pathToStopWords = contextPath + "stop_words_1.txt";
    static String pathToOutputFile = "[PATH_TO_LOG_OUTPUT_FILE]";
    static String pathToSentences = "[PATH_TO_CORPUS]";
    static WordNetDatabase database = WordNetDatabase.getFileInstance();

    public static void main(String[] args) {
        System.setProperty("wordnet.database.dir", "[PATH_TO_WN_DIR]");
        File sentencesFile = new File(pathToSentences);
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(sentencesFile)));
            String sentence = "";
            HashMap<String, SemTypePair> pairs = new HashMap<String, SemTypePair>();
            while((sentence = in.readLine()) != null) {
                sentence = sentence.replace(".","").replace("?","");
                HashMap<String, String> lemmasDictionary = getLemmasDictionary(sentence);
                String lemmatizedVerb = "eat";
                String verb = getVerbFromLemma(lemmatizedVerb, lemmasDictionary);
                SemanticGraph parsingTree = getStanfordParsingTree(sentence);
                Synset bestSenseVerb = disambiguateWord(verb, sentence, "verb");
                System.out.println(sentence);
                IndexedWord idxVerb = getNodeVerb(verb, parsingTree);
                GrammaticalRelation subjRelation = GrammaticalRelation.valueOf("nsubj");
                GrammaticalRelation objRelation = GrammaticalRelation.valueOf("dobj");
                if (idxVerb == null)
                    System.out.println("non c'è verbo");
                IndexedWord subj = getNSubjSentence(idxVerb, parsingTree);
                IndexedWord obj = parsingTree.getChildWithReln(idxVerb, objRelation);

                String subject = "";
                String object = "";
                Synset bestSenseSubj = null;
                Synset bestSenseObj = null;
                SemTypePair pair = new SemTypePair();
                if (subj != null) {
                    subject = lemmasDictionary.get(subj.backingLabel().value());
                    Synset synSubj = null;
                    if (!isPronoun(subject)) {
                        bestSenseSubj = disambiguateWord(subject, sentence, "noun");
                        synSubj = getSuperSense(bestSenseSubj);
                    } else {
                        synSubj = getSuperSense(subject);
                    }
                    pair.setSemType1(synSubj.getWordForms()[0]);
                }
                if (obj != null) {
                    object = lemmasDictionary.get(obj.backingLabel().value());
                    bestSenseObj = disambiguateWord(object, sentence, "noun");
                    Synset synObj = getSuperSense(bestSenseObj);
                    pair.setSemType2(synObj.getWordForms()[0]);
                }
                pairs.put(sentence, pair);
            }
            ArrayList<SemTypePair> values = new ArrayList<SemTypePair>();
            Iterator<String> it = pairs.keySet().iterator();
            while(it.hasNext()){
                String key = it.next();
                SemTypePair pair = pairs.get(key);
                values.add(pair);
                System.out.println("[" + key + "] -> " + pair.toString());
            }
            HashMap<SemTypePair,Integer> results = toFrequencesMap(values);
            printClusters(results);
            in.close();
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }

    /**
     * Stampa a video i patterns semantici con le relative frequenze.
     * @param results mappa chiave-valore contenente i pattern semantici con le relative frequenze calcolate.
     */
    private static void printClusters(HashMap<SemTypePair, Integer> results){
        Set<SemTypePair> keys = results.keySet();
        Iterator<SemTypePair> it = keys.iterator();
        System.out.println("\n");
        while(it.hasNext()){
            SemTypePair pair = it.next();
            Integer freq = results.get(pair);
            System.out.println(pair.toString() +" => freq: " + freq);
        }
    }

    /**
     * Data una lista di pattern semantici in input, produce una mappa che ne aggrega il contenuto eliminando i duplicati
     * e calcolando e salvando le relative frequenze
     * @param pairs lista di pattern semantici da aggregare
     * @return mappa di coppie <pattern semantico, frequenza>
     */
    private static HashMap<SemTypePair,Integer> toFrequencesMap(ArrayList<SemTypePair> pairs) {
        HashMap<SemTypePair, Integer> results = new HashMap<SemTypePair,Integer>();
        for(int i = 0; i < pairs.size(); i++) {
            if (!results.containsKey(pairs.get(i))){
                results.put(pairs.get(i), 1);
            }else{
                Integer freq = results.get(pairs.get(i));
                freq++;
                results.replace(pairs.get(i),freq);
            }
        }
        return results;
    }

    /**
     * Questo metodo trova e restituisce, se presente, il lessema del verbo presente in una lista di coppie <lessema, lemma>
     * @param lemma il lemma del verbo da verificare
     * @param lemmas la lista di coppie <lessema, lemma>
     * @return il lessema del verbo,se presente; stringa vuota altrimenti.
     */
    static String getVerbFromLemma(String lemma, HashMap<String, String> lemmas){
        String verb = "";
        Set<String> keys = lemmas.keySet();
        Iterator<String> it = keys.iterator();
        boolean found = false;
        while(it.hasNext() && !found) {
            String key = it.next();
            String value = lemmas.get(key);
            if (value.equals(lemma)){
                verb = key;
                found = true;
            }
        }
        return verb;
    }

    /**
     * Trova e restituisce, se presente l'istanza IndexedWord relativa al soggetto del verbo della frase parsificata nell'albero a
     * dipendenze passato in input.
     * @param verb l'istanza IndexedWord del verbo per cui si vuole ricavare il soggetto.
     * @param depTree albero di parsing a dipendenze.
     * @return l'istanza IndexedWord del soggetto del verbo; null altrimenti.
     */
    static IndexedWord getNSubjSentence(IndexedWord verb, SemanticGraph depTree){
        IndexedWord word = null;
        GrammaticalRelation nsubjRelation = GrammaticalRelation.valueOf("nsubj");
        GrammaticalRelation xcompRelation = GrammaticalRelation.valueOf("xcomp");
        Set<IndexedWord> xcompParents = depTree.getParentsWithReln(verb, xcompRelation);
        IndexedWord parent = null;
        if (!xcompParents.isEmpty()) {
            Iterator<IndexedWord> itXcomp = xcompParents.iterator();
            parent = itXcomp.next();
            return getNSubjSentence(parent, depTree);
        }else {
            IndexedWord subj = depTree.getChildWithReln(verb, nsubjRelation);
            if(subj != null)
                return subj;
            else
                return null;
        }
    }

    /**
     * Trova e restituisce, se presente, l'istanza IndexedWord relativa al verbo passato in input all'interno di un albero
     * di parsing a dipendenze.
     * @param verb la stringa del verbo da cercare.
     * @param depTree l'albero di parsing a dipendenze in cui effettuare la ricerca.
     * @return l'istanza IndexedWord associata al verbo se presente; null altrimenti.
     */
    static IndexedWord getNodeVerb(String verb, SemanticGraph depTree){
        IndexedWord word = null;
        IndexedWord w;
        for(int i = 0; i <= depTree.size(); i++){
            if(i == 0)
                w = depTree.getFirstRoot();
            else
                w = depTree.getNodeByIndex(i);
            if(w.backingLabel().value().equals(verb)){
                word = w;
                break;
            }
        }
        return word;
    }

    /**
     * Restituisce il Synset di WordNet associato al super-senso della parola data in input. Se la parola non è presente
     * in WordNet viene restituito un Synset di un super-senso fittizio a seconda dei casi.
     * @param word stringa relativa allla parola per la quale si vuole ricavare il super-senso.
     * @return il Synset associato al super-senso della parola.
     */
    static Synset getSuperSense(String word){
        Synset superSense = null;
        IDictionary dict = openMITWordNet();
        Synset[] synsets = null;
        if(isPronoun(word) || isProperNoun(word)) {
            synsets = database.getSynsets("person", SynsetType.NOUN);
        }else{
            IIndexWord idxWord = dict.getIndexWord(word.toLowerCase(), POS.NOUN);
            ISynset synset = null;
            if(idxWord != null) {
                IWordID wordID = idxWord.getWordIDs().get(0);
                IWord w = null;
                w = dict.getWord(wordID);
                synset = w.getSynset();
                String superSenseName = synset.getLexicalFile().getName().replace("noun.","");
                synsets = database.getSynsets(superSenseName, SynsetType.NOUN);
            }else{
                synsets = database.getSynsets("entity",SynsetType.NOUN);
            }
        }
        superSense = synsets[0];
        return superSense;
    }

    /**
     * Restituisce il Synset di WordNet associato al super-senso della parola data in input. Se la parola non è presente
     * in WordNet viene restituito un Synset di un super-senso fittizio a seconda dei casi.
     * @param syns Synset di WordNet associato al concetto.
     * @return il Synset relativo al super-senso del Synset passato in input.
     */
    static Synset getSuperSense(Synset syns){
        Synset superSense = null;
        String word = syns.getWordForms()[0];
        String inputGloss = syns.getDefinition();
        IDictionary dict = openMITWordNet();
        Synset[] synsets = null;
        if(isPronoun(word) || isProperNoun(word)) {
            synsets = database.getSynsets("person", SynsetType.NOUN);
        }else{
            IIndexWord idxWord = dict.getIndexWord(word.toLowerCase(), POS.NOUN);
            ISynset synset = null;
            if(idxWord != null) {
                List<IWordID> wordID =  idxWord.getWordIDs();
                Iterator<IWordID> it = wordID.iterator();
                IWord w = null;
                while(it.hasNext()){
                    w = dict.getWord(it.next());
                    String gloss = w.getSynset().getGloss();
                    if(gloss.contains(inputGloss) || inputGloss.contains(gloss)){
                        break;
                    }
                }
                if(w != null)
                    synset = w.getSynset();
                String superSenseName = synset.getLexicalFile().getName().replace("noun.","");
                synsets = database.getSynsets(superSenseName, SynsetType.NOUN);
            }else{
                synsets = database.getSynsets("entity",SynsetType.NOUN);
            }
        }
        superSense = synsets[0];
        return superSense;
    }

    /**
     * Controlla se la parola passata in input è un nome proprio o meno.
     * @param word la parola da controllare.
     * @return true se la parola in input è un nome proprio; false altrimenti.
     */
    static boolean isProperNoun(String word){
        return Character.isUpperCase(word.charAt(0));
    }

    /**
     * Controlla se la parola data in input è un pronome nominale o meno.
     * @param word la parola da controllare.
     * @return true se la parola in input è un pronome nominale; false altrimenti.
     */
    static boolean isPronoun(String word){
       return word.toLowerCase().matches("i|you|he|she|it|we|they|me|her|him|them|us");
    }

    /**
     * Effettua la disambiguazione di una parola all'interno di una frase data in input, restituendone il relativo Synset
     * di WordNet. Produce un file di log all'interno del quale riporta i risultati calcolati.
     * @param word stringa che rappresenta la parola da disambiguare
     * @param sentence frase in cui disambiguare la parola
     * @param type tipo grammaticale associato alla parola (nome, verbo, aggettivo ecc.)
     * @return Synset di WordNet relativo alla parola disambiguata nella frase.
     */
    static Synset disambiguateWord(String word, String sentence, String type){
        Synset bestSense = null;
        try{
            File f = new File(pathToStopWords);
            File log = new File(pathToOutputFile);
            BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
            ArrayList<String> stopWords = new ArrayList<String>();
            String str = null;
            while ((str = bf.readLine()) != null) {
                if (!str.equals("")) {
                    stopWords.add(str);
                }
            }
            String line = null;
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log)));
            runProcess("pwd");
            runProcess("javac  -classpath src/stemmer Stemmer.java");
            ArrayList<String> filteredWords = filterStopWords(sentence, stopWords);
            Synset[] synsets = null;
            if(type.equalsIgnoreCase("verb"))
                synsets = database.getSynsets(word, SynsetType.VERB);
            else{
                synsets = database.getSynsets(word, SynsetType.NOUN);
                if(synsets == null || synsets.length == 0)
                    synsets = database.getSynsets("entity",SynsetType.NOUN);
            }
            bestSense = leskAlgorithm(synsets, sentence, stopWords);
            out.write("MAIN: sentece: " + sentence + "\t word: " + word + "\nbest sense is [" + bestSense.getWordForms()[0] + "] => (" + bestSense.getDefinition() + ")\n");
            out.close();
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return  bestSense;
    }

    /**
     * Apre e restituisce l'istanza IDictionary relativa al database WordNet (MIT jwi api)
     * @return
     */
    static IDictionary openMITWordNet(){
        String path = "/home/alessandro/Documents/Università/TLN/WordNet-3.0/dict";
        IDictionary dict = null;
        try {
            URL url = new URL("file", null, path);
            dict = new Dictionary(url);
            dict.open();
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return dict;
    }

    /**
     * Data la frase passata in input, effettua la lemmatizzazione delle parole e restituisce la lista di coppie <lessema, lemma>
     * associata alla frase.
     * @param sentence la frase da lemmatizzare.
     * @return la lista di coppie associata alla frase passata in input.s
     */
    static HashMap<String,String> getLemmasDictionary(String sentence){
        Properties props = new Properties();
        props.put("annotators", "tokenize,ssplit,pos,lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation document = new Annotation(sentence);
        pipeline.annotate(document);
        CoreMap sentenceMap = document.get(SentencesAnnotation.class).get(0);
        HashMap<String,String> lemmas = new HashMap<>();
        for(CoreLabel token : sentenceMap.get(TokensAnnotation.class)){
            String text = token.get(CoreAnnotations.TextAnnotation.class);
            String lemma = token.get(LemmaAnnotation.class);
            lemmas.put(text,lemma);
        }
        return lemmas;
    }

    /**
     * Data la frase in input produce l'albero di parsing a dipendenze ad essa associata (Stanford NLP).
     * @param sentence la frase da parsificare.
     * @return istanza SemanticGraph che rappresenta l'albero a dipendenze associato alla frase.
     */
    static SemanticGraph getStanfordParsingTree(String sentence){
        SemanticGraph parsingTree = null;
        Properties props = new Properties();
        props.put("annotators", "tokenize,ssplit,pos,parse");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation document = new Annotation(sentence);
        pipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for(CoreMap sent : sentences) {
            for (CoreLabel token : sent.get(TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            }
            parsingTree = sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        }
        return parsingTree;
    }

    /**
     * Implementa l'algoritmo di lesk per effettuare la disambiguazione di una parola.
     * @param syns
     * @param sentence
     * @param stopWords
     * @return
     */
    static Synset leskAlgorithm(Synset[] syns, String sentence, ArrayList<String> stopWords) {
        Synset bestSynset = syns[0];
        int maxOverlap = 0;
        int overlap;
        List<String> stemmedCtx = computeStemsWithStanford(filterStopWords(sentence, stopWords));
        for (Synset synset : syns) {
            overlap = 0;
            String bestSense = synset.getWordForms()[0];
            String gloss = synset.getDefinition().replaceAll(";", "");
            String[] examples = synset.getUsageExamples();
            List<String> stemmedGloss = computeStemsWithStanford(filterStopWords(gloss, stopWords));
            List<String> stemmedExamples = new ArrayList<String>();
            for (String example : examples) {
                example = example.replaceAll("\"", "").replaceAll(";", "").replaceAll(",", "").replaceAll("\\.", "");
                stemmedExamples.addAll(computeStemsWithStanford(filterStopWords(example, stopWords)));
            }
            if (stemmedExamples != null) {
                stemmedGloss.addAll(stemmedExamples);
            }
            overlap = computeOverlap(stemmedCtx, stemmedGloss);
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestSynset = synset;
            }
        }
        return bestSynset;
    }

    /**
     * Calcola l'overlap tra il contesto di disambiguazione di un concetto e la sua signature.
     * @param context contesto di disambiguazione
     * @param signature signature (glossa + esempi in WordNet)
     * @return il valore numerico di sovrapposizione.
     */
    private static int computeOverlap(List<String> context, List<String> signature) {
        int overlap = 0;
        for (Iterator<String> it = context.iterator(); it.hasNext();) {
            String c = it.next();
            if (signature.contains(c)) {
                overlap++;
            }
        }
        return overlap;
    }

    /**
     * Questo metodo effettua il filtraggio di stop-words per la frase passata in input
     * @param sentence frase da filtrare
     * @param stopWords lista di stop-words
     * @return lista delle parole filtrate.
     */
    private static ArrayList<String> filterStopWords(String sentence, ArrayList<String> stopWords) {
        ArrayList<String> filteredWords = new ArrayList<String>();
        String[] tokenizedWords = sentence.split(" ");
        for (String s : tokenizedWords) {
            if (!stopWords.contains(s)) {
                filteredWords.add(s);
            }
        }
        return filteredWords;
    }

    /**
     *
     * @param filteredWords
     * @return
     */
    private static List<String> computeStemsWithStanford(ArrayList<String> filteredWords){
        List<String> stemmedWords = new ArrayList<>();
        StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties() {
            {
                setProperty("annotators", "tokenize,ssplit,pos,lemma");
            }
        });


        for (String word : filteredWords) {
            Annotation tokenAnnotation = new Annotation(word);
            pipeline.annotate(tokenAnnotation);  // necessary for the LemmaAnnotation to be set.
            List<CoreMap> list = tokenAnnotation.get(SentencesAnnotation.class);
            String tokenLemma = list
                    .get(0).get(TokensAnnotation.class)
                    .get(0).get(LemmaAnnotation.class);
            stemmedWords.add(tokenLemma);
        }
        return stemmedWords;
    }

    /**
     *
     * @param command
     * @throws Exception
     */
    private static void runProcess(String command) throws Exception {
        Process pro = Runtime.getRuntime().exec(command);
        pro.waitFor();
    }
}
