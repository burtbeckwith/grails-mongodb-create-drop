package grails.plugin.mongodbcreatedrop

import static grails.plugin.mongodbcreatedrop.CreateDropType.*

class MongoCreateDropUtils {

	static final DEFAULT_KEEP_COLLECTIONS_PATTERN = "system\\..*"
	
	CreateDropType type		= none
	def collectionsRegex	= DEFAULT_KEEP_COLLECTIONS_PATTERN
	def	databaseName
	def	username
	transient def password
	transient def db
	
	MongoCreateDropUtils(grailsApplication, dbFactory = new MongoDbFactory()) {
		def dbConfig		= grailsApplication.config.grails.mongo
		def host			= dbConfig.host
		type				= getTypeFrom(dbConfig)				
		databaseName		= dbConfig.databaseName
		username			= dbConfig.username 
		password			= dbConfig.password 
		collectionsRegex	= getRegexFrom(dbConfig)
		validateConfig()
		db = dbFactory.getByName(host, databaseName)
	}
	
	void createDrop() {
		if (doAbortBecauseNothingToDo()) return
		authenticate()
		if (type == database) {
			dropDatabase()
		} else if (type == drop) {
			dropAll(collectionsWithNameMatching())
		} else {
			dropAll(collectionsWithNameNotMatching())
		}
	}
	
	private boolean doAbortBecauseNothingToDo() {
		boolean isNothingToDo = type == none
		if (isNothingToDo) {
			log.debug "Nothing to do for type='$type'. Aborting createDrop."
		} 
		isNothingToDo
	}
	
	private def getTypeFrom(config) {
		try {
			return CreateDropType.lookup(config?.createDrop)
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid value for createDrop: $config.createDrop")
		}
	}
	
	private def getRegexFrom(config) {
		cleanRegexConfig(CreateDropType.getValue(config.createDrop)) ?: DEFAULT_KEEP_COLLECTIONS_PATTERN
	}
	
	private def cleanRegexConfig(regex) {
		if (regex?.respondsTo("trim")) {
			return regex.trim()
		}
		regex
	}
	
	private boolean authenticate() {
		boolean isAuthMode = credentialsProvided
		if (isAuthMode) {
			log.debug "Authenticating..."
			db.authenticate(username, password as char[])
		}
		isAuthMode
	}
	
	private def collectionsWithNameNotMatching() {
		findCollectionNamesWhere() {!it.matches(collectionsRegex) }
	}
	
	private def collectionsWithNameMatching() {
		findCollectionNamesWhere() { it.matches(collectionsRegex) }
	}
	
	private def findCollectionNamesWhere(condition) {
		def allCollectionNames = db.getCollectionNames()
		log.debug "All collections: $allCollectionNames"
		allCollectionNames.findAll { condition(it) }
	}

	
	private void dropDatabase() {
		log.debug "Dropping database: $databaseName"
		db.dropDatabase()
	}
	
	private void dropAll(collectionNames) {
		collectionNames.each {
			log.debug "Dropping collection: $it"
			db.getCollection(it).drop()
		}
	}
	
	private boolean isCredentialsProvided() {
		username || password
	}

	private void validateConfig() {
		failIfDatabaseNameMissing() 
	}
	
	private void failIfDatabaseNameMissing() {
		if (!databaseName) {
			throw new IllegalArgumentException("'grails.mongo.databaseName' is missing.")
		}
	}
		
}