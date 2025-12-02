db.createUser(
  { user: "security_officer",
    pwd: "h3ll0th3r3",
    roles: [ { db: "admin", role: "userAdmin" } ]
  }
);


db.createUser(
  { user: "dba",
    pwd: "c1lynd3rs",
    roles: [ { db: "admin", role: "dbAdmin" } ]
  }
);

db.grantRolesToUser( "dba",  [ { db: "playground", role: "dbOwner"  } ] );

db.runCommand( { rolesInfo: { role: "dbOwner", db: "playground" }, showPrivileges: true} );

db.createUser(
  { user: "gakins",
    pwd: "E5press0",
    roles: [{ db: "user", role: "readWrite" }, { db: "madiecqllibrary", role: "readWrite" }, { db: "madie", role: "readWrite" } ]
  }
);


