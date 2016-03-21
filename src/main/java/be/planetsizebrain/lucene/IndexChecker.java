package be.planetsizebrain.lucene;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexChecker {

	private static final Logger LOGGER = LoggerFactory.getLogger(IndexChecker.class);

	private List<Directory> indexDirectories = new ArrayList<>();
	private List<IndexReader> indexReaders = new ArrayList<>();
	private IndexSearcher indexSearcher;

	public static void main(String[] args) {
		if (args.length != 4) {
			LOGGER.warn("Incorrect nr. of parameters provided to index checker: wanted 4, got {}", args.length);
		} else {
			File indexLocation = new File(args[0]);
			String query = args[1];
			String fieldToCheck = args[2];
			String[] fieldsToReturn = args[3].split(",");

			try {
				IndexChecker checker = new IndexChecker(indexLocation);
				checker.search(query, fieldToCheck, fieldsToReturn);
			} catch (Exception e) {
				LOGGER.error("Problem doing index check", e);
			}
		}
	}

	// http://www.gossamer-threads.com/lists/lucene/java-user/128270
	public IndexChecker(File indexLocation) throws IOException {
		for (String dir : indexLocation.list(DirectoryFileFilter.INSTANCE)) {
			File indexDir = new File(indexLocation.getAbsolutePath() + File.separator + dir);

			LOGGER.info("Adding index directory '{}' to search", indexDir.getAbsolutePath());

			Directory directory = FSDirectory.open(indexDir);
			indexDirectories.add(directory);
			indexReaders.add(IndexReader.open(directory, true));
		}

		IndexReader indexReader = new MultiReader(indexReaders.toArray(new IndexReader[0]));
		this.indexSearcher = new IndexSearcher(indexReader);
	}

	public void search(String queryString, String fieldToCheck, String[] fieldsToReturn) throws IOException, ParseException {
		try {
			QueryParser parser = new QueryParser(Version.LUCENE_35, "entryClassName", new KeywordAnalyzer());
			parser.setAllowLeadingWildcard(true);
        	Query query = parser.parse(queryString);

			TopDocs topDocs = indexSearcher.search(query, indexSearcher.maxDoc());
			ScoreDoc[] hits = topDocs.scoreDocs;

			LOGGER.info("Found {} possible incorrect documents, checking '{}' field...", hits.length, fieldToCheck);

			int total = 0;
			int totalEmpty = 0;
			for (int i = 0; i < hits.length; i++) {
				int docId = hits[i].doc;
				Document document = indexSearcher.doc(docId);
				String groupRoleId = document.get(fieldToCheck);
				if (groupRoleId != null && !groupRoleId.isEmpty()) {
					total++;
					LOGGER.info("Found document with incorrect value for '{}': {}", fieldToCheck, getFieldValues(fieldsToReturn, document));
				} else {
					totalEmpty++;
					LOGGER.info("Found document with empty value for '{}': {}", fieldToCheck, getFieldValues(fieldsToReturn, document));
				}
			}

			LOGGER.info("Done. Found {} incorrect and {} empty entries", total, totalEmpty);
		} finally {
			if (indexSearcher != null) {
				indexSearcher.close();
			}

			for (Directory directory : indexDirectories) {
				if (directory != null) {
					directory.close();
				}
			}
		}
	}

	private String getFieldValues(String[] fieldsToReturn, Document document) {
		return Arrays.stream(fieldsToReturn)
				.map(fieldName -> "(" + fieldName + ": " + document.get(fieldName) + ")")
				.reduce((value1, value2) -> value1 + ", " + value2)
				.orElse("");
	}
}