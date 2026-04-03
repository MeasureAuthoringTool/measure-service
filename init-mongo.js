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

// Switch context to the madie database
db = db.getSiblingDB("madie");

var organizations = [
    {
        name: "Joint Commission",
        oid: "1.3.6.1.4.1.33895",
        url: "https://www.jointcommission.org/",
        _class: "gov.cms.madie.models.common.Organization"
    },
    {
        name: "ICF",
        oid: "e96078ba-a69f-11ea-bb37-0242ac130002",
        url: "https://www.icf.com/",
        _class: "gov.cms.madie.models.common.Organization"
    },
    {
        name: "SemanticBits",
        oid: "02c84f54-919b-4464-bf51-a1438f2710e2",
        url: "https://semanticbits.com/",
        _class: "gov.cms.madie.models.common.Organization"
    }
];

organizations.forEach(function(org) {
    db.organization.updateOne(
        { name: org.name },
        { $setOnInsert: org },
        { upsert: true }
    );
});


