package au.com.museumvictoria.fieldguide.bunurong.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.DatabaseUtils.InsertHelper;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import au.com.museumvictoria.fieldguide.bunurong.model.ConservationStatuses;
import au.com.museumvictoria.fieldguide.bunurong.model.Images;
import au.com.museumvictoria.fieldguide.bunurong.model.Species;
import au.com.museumvictoria.fieldguide.bunurong.util.Utilities;

/**
 * <p>Helper class to do all database related activities</p>
 * 
 * TODO: Clean this class up big time.
 * TODO: Remove deprecated code
 * 
 * @author Ajay Ranipeta <ajay.ranipeta@gmail.com>
 *
 */
public class FieldGuideDatabase {

	private static final String TAG = "Bunurong.FieldGuideDatabase";

	// database
	private static int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "fieldguide";
	private static final String SPECIES_TABLE_NAME = "species";
	private static final String IMAGES_TABLE_NAME = "images";

	// column mapping
	public static final String SPECIES_ID = "_id";
	public static final String SPECIES_IDENTIFIER = "identifier";
	public static final String SPECIES_LABEL = "label";
	public static final String SPECIES_SUBLABEL = "sublabel";
	public static final String SPECIES_THUMBNAIL = "squareThumbnail";
	public static final String SPECIES_GROUP = "groupLabel";
	public static final String SPECIES_SUBGROUP = "subgroupLabel";
	public static final String SPECIES_SEARCHTEXT = "searchText";
	public static final String SPECIES_SEARCHICON = "searchIcon";

	private final FieldGuideOpenHelper mDatabaseOpenHelper;
	private SQLiteDatabase mDatabase;
	private static FieldGuideDatabase mInstance = null;
	
	private int currCount = 0;
	private int totalCount = 0;

	public FieldGuideDatabase(Context context) {
		mDatabaseOpenHelper = new FieldGuideOpenHelper(context);
	}
	
	public static FieldGuideDatabase getInstance(Context ctx) {
	     
	    // Use the application context, which will ensure that you 
	    // don't accidentally leak an Activity's context.
	    // See this article for more information: http://bit.ly/6LRzfx
	    if (mInstance == null) {
	      mInstance = new FieldGuideDatabase(ctx.getApplicationContext());
	    }
	    return mInstance;
	  }
	
	public void open() throws SQLException {
		mDatabase = mDatabaseOpenHelper.getReadableDatabase();
    }
	
	public void close() {
		//mDatabaseOpenHelper.close();
    }
	
	public int getCurrCount() {
		return currCount;
	}

	public int getTotalCount() {
		return totalCount;
	}
	
	public long getSpeciesCount() {
		return DatabaseUtils.queryNumEntries(mDatabase, SPECIES_TABLE_NAME); 
	}
	
	public Cursor getSpeciesMatches(String query) {
		
	    String[] columns = new String[] { BaseColumns._ID, SPECIES_IDENTIFIER, SPECIES_LABEL, SPECIES_SUBLABEL, SPECIES_THUMBNAIL };

	    return getSpeciesMatches(query, columns);
	}

	public Cursor getSpeciesMatches(String query, String[] columns) {
		
		Log.d(TAG, "Searching species for " + query); 
		
		String selection = SPECIES_SEARCHTEXT + " LIKE ?";
	    String[] selectionArgs = new String[] {"%"+query+"%"};

	    return query(SPECIES_TABLE_NAME, columns, selection, selectionArgs, null, SPECIES_LABEL);
	}

	public Cursor getSpeciesList(String groupLabel) {
		String[] columns = new String[] { BaseColumns._ID, SPECIES_IDENTIFIER, SPECIES_LABEL, SPECIES_SUBLABEL, SPECIES_THUMBNAIL, SPECIES_SUBGROUP };
		String selection = null;
		String[] selectionArgs = null;
		String groupBy = null;
		String orderBy = SPECIES_LABEL;

		// get species for a given group if available
		// or default to all species
		if (groupLabel != null && !groupLabel.equals("ALL")) {
			// do nothing
			Log.d(TAG, "Getting species list for '" + groupLabel + "'");
			selection = SPECIES_GROUP + " = ?";
			selectionArgs = new String[] { groupLabel };
			orderBy = SPECIES_SUBGROUP;
		}

		Cursor cursor = query(SPECIES_TABLE_NAME, columns, selection, selectionArgs, groupBy, orderBy);

		if (cursor == null) {
			Log.w(TAG, "Species list not available");
			return null;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			Log.w(TAG, "Species list not available");
			return null;
		}

		return cursor;

	}

	public Cursor getSpeciesGroups() {

		Log.w(TAG, "Getting species groups");

		String[] columns = new String[] { BaseColumns._ID, SPECIES_GROUP };
		Cursor cursor = query(SPECIES_TABLE_NAME, columns, null, null, SPECIES_GROUP, SPECIES_GROUP);

		if (cursor == null) {
			Log.w(TAG, "No species groups available");
			return null;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			Log.w(TAG, "No species groups available");
			return null;
		}

		return cursor;

	}

	/**
	 * Performs a database query.
	 * 
	 * @param columns
	 *            The columns to return
	 * @param selection
	 *            The selection clause
	 * @param selectionArgs
	 *            Selection arguments for "?" components in the selection
	 * @param groupBy
	 *            The GROUP BY column name
	 * @param orderBy
	 *            The ORDER BY column name
	 * @return A Cursor over all rows matching the query
	 */
	private Cursor query(String tables, String[] columns, String selection, String[] selectionArgs, String groupBy,
			String orderBy) {
		/*
		 * The SQLiteBuilder provides a map for all possible columns requested
		 * to actual columns in the database, creating a simple column alias
		 * mechanism by which the ContentProvider does not need to know the real
		 * column names
		 */
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		builder.setTables(tables);

		Cursor cursor = builder.query(mDatabaseOpenHelper.getReadableDatabase(), columns, selection, selectionArgs,
				groupBy, null, orderBy);

		if (cursor == null) {
			Log.w(TAG, "No data available from the query");
			return null;
		} else if (!cursor.moveToFirst()) {
			Log.w(TAG, "No data available from the query");
			cursor.close();
			return null;
		}
		
		Log.d(TAG, "Returning " + cursor.getCount() + " records");
		
		return cursor;
	}

	/**
	 * Returns a Cursor positioned at the species detail specified by rowId
	 * 
	 * @param identifier
	 *            species identifier
	 * @param columns
	 *            The columns to include, if null then all are included
	 * @return Cursor positioned to matching word, or null if not found.
	 */
	public Cursor getSpeciesDetails(String identifier, String[] columns) {
		String selection = BaseColumns._ID + " = ?";
		String[] selectionArgs = new String[] { identifier };

		Log.d(TAG, "Getting species details for: " + identifier); 

		return query(SPECIES_TABLE_NAME, columns, selection, selectionArgs, null, null);
	}

	public Cursor getSpeciesImages(String identifier) {
		String selection = SPECIES_IDENTIFIER + " = ?";
		String[] selectionArgs = new String[] { identifier };

		Log.d(TAG, "Getting species images for: " + identifier); 

		return query(IMAGES_TABLE_NAME, null, selection, selectionArgs, null, null);
	}
	
	public List<Species> getAllSpeciesList() {
		List<Species> splist = new ArrayList<Species>();

		Cursor cursor = getSpeciesList(null);

		if (cursor == null) {
			return null;
		}

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Species sp = cursorToSpecies(cursor);
			splist.add(sp);
			cursor.moveToNext();
		}
		// Make sure to close the cursor
		cursor.close();

		return splist;
	}

	private Species cursorToSpecies(Cursor cursor) {
		Species sp = new Species();

		sp.setIdentifier(cursor.getString(1));
		sp.setLabel(cursor.getString(2));
		sp.setSublabel(cursor.getString(3));
		sp.setSquareThumbnail(cursor.getString(4));

		return sp;
	}

	private class FieldGuideOpenHelper extends SQLiteOpenHelper {

		private final Context mHelperContext;
		private SQLiteDatabase mDatabase;
		//private final SQLiteDatabase.CursorFactory cursorFactory = new SQLiteCursorFactory(true); 

		// IF NOT EXISTS
		private static final String SPECIES_TABLE_CREATE = "CREATE TABLE "
				+ SPECIES_TABLE_NAME
				+ " (_id INTEGER PRIMARY KEY  AUTOINCREMENT  NOT NULL, identifier TEXT, label TEXT, sublabel TEXT, searchText TEXT, squareThumbnail TEXT, searchIcon TEXT, groupLabel TEXT, subgroupLabel TEXT, description TEXT, bite TEXT, biology TEXT, diet TEXT, habitat TEXT, nativeStatus TEXT, distinctive TEXT, distribution TEXT, conservationStatusDSE TEXT, conservationStatusEPBC TEXT, conservationStatusIUCN TEXT, depth TEXT, location TEXT, isCommercial BOOL, taxaPhylum TEXT, taxaClass TEXT, taxaOrder TEXT, taxaFamily TEXT, taxaGenus TEXT, taxaSpecies TEXT, commonNames TEXT, otherNames TEXT); ";
		private static final String IMAGES_TABLE_CREATE = "CREATE TABLE "
				+ IMAGES_TABLE_NAME
				+ " (_id INTEGER PRIMARY KEY  AUTOINCREMENT  NOT NULL, identifier TEXT, filename TEXT, caption TEXT, credit TEXT); ";

		public FieldGuideOpenHelper(Context context) {
			
			// Uncomment the following line for SQL debug statements
			// make sure you comment out the following 'super' statment
			//super(context, DATABASE_NAME, new SQLiteCursorFactory(true), DATABASE_VERSION);
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
			
			mHelperContext = context;

			// Log.w(TAG, "Deleting old db");
			// mHelperContext.deleteDatabase(DATABASE_NAME);

		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.w(TAG, "Creating new db");
			mDatabase = db;

			Log.w(TAG, "Creating tables");
			mDatabase.execSQL(SPECIES_TABLE_CREATE);
			mDatabase.execSQL(IMAGES_TABLE_CREATE);

			Log.w(TAG, "Loading data via loadFieldGuideData()");
			loadFieldGuideData();

			Log.w(TAG, "Done loading data");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
			
			db.execSQL("DROP TABLE IF EXISTS " + SPECIES_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + IMAGES_TABLE_NAME);
			onCreate(db);
		}

		/**
		 * Starts a thread to load the database table with words
		 */
		private void loadFieldGuideData() {
			new Thread(new Runnable() {
				public void run() {
					try {
						Log.d(TAG, "Loading data via loadData()");
						loadData();
					} catch (IOException e) {
						throw new RuntimeException(e);
					} catch (JSONException e) {
						throw new RuntimeException(e);
					}
				}
			}).start();
		}

		private void loadData() throws IOException, JSONException {

			Log.d(TAG, "in loadData()");

			Log.d(TAG, "Setting Species IH");
			InsertHelper ih1 = new InsertHelper(mDatabase, SPECIES_TABLE_NAME);
			Log.d(TAG, "Setting Images IH");
			InsertHelper ih2 = new InsertHelper(mDatabase, IMAGES_TABLE_NAME);

			Log.d(TAG, "Setting identifierColumn");
			final int identifierColumn = ih1.getColumnIndex("identifier");
			final int labelColumn = ih1.getColumnIndex("label");
			final int subLabelColumn = ih1.getColumnIndex("sublabel");
			final int searchTextColumn = ih1.getColumnIndex("searchText");
			final int squareThumbnailColumn = ih1.getColumnIndex("squareThumbnail");
			final int groupColumn = ih1.getColumnIndex("groupLabel");
			final int subGroupColumn = ih1.getColumnIndex("subgroupLabel");
			final int descriptionColumn = ih1.getColumnIndex("description");
			final int biteColumn = ih1.getColumnIndex("bite");
			final int biologyColumn = ih1.getColumnIndex("biology");
			final int dietColumn = ih1.getColumnIndex("diet");
			final int habitatColumn = ih1.getColumnIndex("habitat");
			final int nativeStatusColumn = ih1.getColumnIndex("nativeStatus");
			final int distinctiveColumn = ih1.getColumnIndex("distinctive");
			final int distributionColumn = ih1.getColumnIndex("distribution");
			final int conservationStatusColumnDSE = ih1.getColumnIndex("conservationStatusDSE");
			final int conservationStatusColumnEPBC = ih1.getColumnIndex("conservationStatusEPBC");
			final int conservationStatusColumnIUCN = ih1.getColumnIndex("conservationStatusIUCN");
			final int depthColumn = ih1.getColumnIndex("depth");
			final int locationColumn = ih1.getColumnIndex("location");
			final int isCommercialColumn = ih1.getColumnIndex("isCommercial");
			final int taxaPhylumColumn = ih1.getColumnIndex("taxaPhylum");
			final int taxaClassColumn = ih1.getColumnIndex("taxaClass");
			final int taxaOrderColumn = ih1.getColumnIndex("taxaOrder");
			final int taxaFamilyColumn = ih1.getColumnIndex("taxaFamily");
			final int taxaGenusColumn = ih1.getColumnIndex("taxaGenus");
			final int taxaSpeciesColumn = ih1.getColumnIndex("taxaSpecies");
			final int commonNameColumn = ih1.getColumnIndex("commonNames");
			final int otherNamesColumn = ih1.getColumnIndex("otherNames");
			final int searchIconColumn = ih1.getColumnIndex("searchIcon");

			Log.d(TAG, "Setting filenameColumn");
			final int filenameColumn = ih2.getColumnIndex("filename");
			final int captionColumn = ih2.getColumnIndex("caption");
			final int creditColumn = ih2.getColumnIndex("credit");
			final int identifierFKColumn = ih2.getColumnIndex("identifier");

			Log.d(TAG, "Getting species data from getData()");

			HashMap<String, Object> data = getData();

			Log.w(TAG, "Loading species data...");
			if (data != null) {
				JsonArray splist = (JsonArray)data.get("data");
				
				totalCount = splist.size(); 
				Log.w(TAG, "Loading " + splist.size() + " records");

				mDatabase.beginTransaction();

				try {
					
					Gson gson = new Gson();
					for (int i=0; i<splist.size(); i++) {
						Species s = gson.fromJson(splist.get(i), Species.class);
						
						ih1.prepareForInsert();

						ih1.bind(identifierColumn, s.getIdentifier());
						ih1.bind(labelColumn, s.getLabel());
						ih1.bind(subLabelColumn, s.getSublabel());
						ih1.bind(searchTextColumn, s.getSearchText());
						ih1.bind(squareThumbnailColumn, s.getSquareThumbnail());
						ih1.bind(searchIconColumn, "content://au.com.museumvictoria.fieldguide.bunurong.FieldGuideAssestsProvider/" + s.getSquareThumbnail());
						ih1.bind(groupColumn, s.getGroup());
						ih1.bind(subGroupColumn, s.getSubgroup());
						ih1.bind(descriptionColumn, s.getDetails().getDescription());
						ih1.bind(biteColumn, s.getDetails().getBite());
						ih1.bind(biologyColumn, s.getDetails().getBiology());
						ih1.bind(dietColumn, s.getDetails().getDiet());
						ih1.bind(habitatColumn, s.getDetails().getHabitat());
						ih1.bind(nativeStatusColumn, s.getDetails().getNativeStatus());
						ih1.bind(distinctiveColumn, s.getDetails().getDistinctive());
						ih1.bind(distributionColumn, s.getDetails().getDistribution());
						
						Iterator<ConservationStatuses> css = s.getDetails().getConservationStatuses().iterator();
						while (css.hasNext()) {
							ConservationStatuses cs = css.next();
							if (cs.getAuthority().startsWith("DSE")) {
								ih1.bind(conservationStatusColumnDSE, cs.getStatus());
							}
							if (cs.getAuthority().startsWith("EPBC")) {
								ih1.bind(conservationStatusColumnEPBC, cs.getStatus());
							}
							if (cs.getAuthority().startsWith("IUCN")) {
								ih1.bind(conservationStatusColumnIUCN, cs.getStatus());
							}
						}
						
						ih1.bind(depthColumn, TextUtils.join(";;", s.getDetails().getDepth()));
						ih1.bind(locationColumn, TextUtils.join(";;", s.getDetails().getLocation()));
						ih1.bind(isCommercialColumn, s.getDetails().isCommercial());
						ih1.bind(taxaPhylumColumn, s.getDetails().getTaxaPhylum());
						ih1.bind(taxaClassColumn, s.getDetails().getTaxaClass());
						ih1.bind(taxaOrderColumn, s.getDetails().getTaxaOrder());
						ih1.bind(taxaFamilyColumn, s.getDetails().getTaxaFamily());
						ih1.bind(taxaGenusColumn, s.getDetails().getTaxaGenus());
						ih1.bind(taxaSpeciesColumn, s.getDetails().getTaxaSpecies());
						ih1.bind(commonNameColumn, s.getDetails().getCommonNames());
						ih1.bind(otherNamesColumn, s.getDetails().getOtherNames());

						ih1.execute();

						Iterator<Images> imgs = s.getImages().iterator();
						while (imgs.hasNext()) {
							Images img = imgs.next();

							ih2.prepareForInsert();

							ih2.bind(filenameColumn, img.getFilename());
							ih2.bind(captionColumn, img.getImageDescription());
							ih2.bind(creditColumn, img.getCredit());
							ih2.bind(identifierFKColumn, s.getIdentifier());

							ih2.execute();
						}
						
						currCount++;

					}

					mDatabase.setTransactionSuccessful();

				} finally {
					ih1.close();
					ih2.close();

					mDatabase.endTransaction();
				}

			}
			Log.d(TAG, "Done loading species data.");

		}

		private HashMap<String, Object> getData() throws IOException, JSONException {
			Log.d(TAG, "Reading species data...");
			
			//JsonReader reader = new JsonReader(new InputStreamReader(mHelperContext.getAssets().open("data/generaData.json")));
			
			// production code for .obb file
			JsonReader reader = new JsonReader(new InputStreamReader(Utilities.getAssetInputStream(mHelperContext, Utilities.SPECIES_DATA_FILE)));
			
			//JsonReader reader = new JsonReader(new InputStreamReader(Utilities.getAssetsFileDescriptor(mHelperContext, "data/generaData.json").createInputStream()));
			
			//Reader reader = new InputStreamReader(mHelperContext.getAssets().open("data/generaData.json")); 
			//HashMap<String, Object> generaData = new Gson().fromJson(reader, HashMap.class);
			
			JsonParser parser = new JsonParser();
			JsonObject json = parser.parse(reader).getAsJsonObject();
			double version = json.get("version").getAsDouble();
			JsonArray speciesdata = json.get("data").getAsJsonArray();
			
			HashMap<String, Object> generaData = new HashMap<String, Object>();
			generaData.put("version", version);
			generaData.put("data", speciesdata);
			
			return generaData; 
		}

	}
	
	
	
	class SQLiteCursorFactory implements CursorFactory {

	    private boolean debugQueries = false;

	    public SQLiteCursorFactory() {
	        this.debugQueries = false;
	    }

	    public SQLiteCursorFactory(boolean debugQueries) {
	        this.debugQueries = debugQueries;
	    }

	    @Override
	    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, 
	                            String editTable, SQLiteQuery query) {
	        if (debugQueries) {
	            Log.d("SQL", query.toString());
	        }
	        return new SQLiteCursor(db, masterQuery, editTable, query);
	    }
	}
	
}
