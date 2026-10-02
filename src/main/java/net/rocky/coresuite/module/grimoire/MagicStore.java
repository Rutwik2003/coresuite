package net.rocky.coresuite.module.grimoire;

import java.sql.*;
import java.util.UUID;
import net.rocky.coresuite.common.storage.DatabaseManager;

/** Shared pool, portable SQL, revision checks and globally single-use tome tokens. */
public final class MagicStore {
  private final DatabaseManager db;

  public MagicStore(DatabaseManager db) {
    this.db = db;
  }

  public void initialize() throws SQLException {
    try (var c = db.getConnection();
        var s = c.createStatement()) {
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS coresuite_magic_profiles (uuid VARCHAR(36) PRIMARY KEY,"
              + " revision BIGINT NOT NULL, payload TEXT NOT NULL)");
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS coresuite_magic_research (receipt VARCHAR(80) PRIMARY KEY,"
              + " uuid VARCHAR(36) NOT NULL, amount INTEGER NOT NULL, claimed INTEGER NOT NULL"
              + " DEFAULT 0)");
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS coresuite_magic_stock (stock_id VARCHAR(128) PRIMARY KEY,"
              + " used INTEGER NOT NULL)");
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS coresuite_magic_shard_rewards (receipt VARCHAR(128) PRIMARY"
              + " KEY, uuid VARCHAR(36) NOT NULL, element VARCHAR(64) NOT NULL, amount INTEGER NOT"
              + " NULL, claimed INTEGER NOT NULL DEFAULT 0)");
      s.executeUpdate(
          "CREATE TABLE IF NOT EXISTS coresuite_magic_tomes (token VARCHAR(36) PRIMARY KEY, spell"
              + " VARCHAR(64) NOT NULL, redeemed_by VARCHAR(36))");
      ensureClaimIndex(c, "coresuite_magic_research", "idx_magic_research_claim");
      ensureClaimIndex(c, "coresuite_magic_shard_rewards", "idx_magic_shard_claim");
    }
  }

  private void ensureClaimIndex(Connection c, String table, String name) throws SQLException {
    try (var indexes = c.getMetaData().getIndexInfo(c.getCatalog(), null, table, false, false)) {
      while (indexes.next()) if (name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return;
    }
    try (var statement = c.createStatement()) {
      statement.executeUpdate("CREATE INDEX " + name + " ON " + table + " (uuid,claimed)");
    }
  }

  public MagicProfile load(UUID id) throws Exception {
    try (var c = db.getConnection();
        var q =
            c.prepareStatement(
                "SELECT revision,payload FROM coresuite_magic_profiles WHERE uuid=?")) {
      q.setString(1, id.toString());
      try (var r = q.executeQuery()) {
        if (r.next()) return MagicProfile.decode(r.getString(2), r.getLong(1));
      }
      var p = new MagicProfile();
      try (var insert =
          c.prepareStatement(
              "INSERT INTO coresuite_magic_profiles(uuid,revision,payload) VALUES (?,0,?)")) {
        insert.setString(1, id.toString());
        insert.setString(2, p.encode());
        insert.executeUpdate();
      }
      return p;
    }
  }

  public void save(UUID id, long expected, String payload) throws SQLException {
    try (var c = db.getConnection()) {
      save(c, id, expected, payload);
    }
  }

  private void save(Connection c, UUID id, long expected, String payload) throws SQLException {
    try (var s =
        c.prepareStatement(
            "UPDATE coresuite_magic_profiles SET revision=?,payload=? WHERE uuid=? AND"
                + " revision=?")) {
      s.setLong(1, expected + 1);
      s.setString(2, payload);
      s.setString(3, id.toString());
      s.setLong(4, expected);
      if (s.executeUpdate() != 1)
        throw new SQLException("Magic account revision conflict for " + id);
    }
  }

  public void award(String receipt, UUID id, int amount) throws SQLException {
    try (var c = db.getConnection()) {
      try (var q =
          c.prepareStatement("SELECT receipt FROM coresuite_magic_research WHERE receipt=?")) {
        q.setString(1, receipt);
        try (var r = q.executeQuery()) {
          if (r.next()) return;
        }
      }
      try (var q =
          c.prepareStatement(
              "INSERT INTO coresuite_magic_research(receipt,uuid,amount) VALUES (?,?,?)")) {
        q.setString(1, receipt);
        q.setString(2, id.toString());
        q.setInt(3, Math.max(0, Math.min(10, amount)));
        q.executeUpdate();
      }
    }
  }

  public boolean awardBossBundle(
      String receipt, UUID id, int research, String element, int shards, String fragment)
      throws SQLException {
    try (var c = db.getConnection()) {
      c.setAutoCommit(false);
      try {
        try (var q =
            c.prepareStatement("SELECT receipt FROM coresuite_magic_research WHERE receipt=?")) {
          q.setString(1, receipt);
          try (var r = q.executeQuery()) {
            if (r.next()) {
              c.rollback();
              return false;
            }
          }
        }
        try (var q =
            c.prepareStatement(
                "INSERT INTO coresuite_magic_research(receipt,uuid,amount) VALUES (?,?,?)")) {
          q.setString(1, receipt);
          q.setString(2, id.toString());
          q.setInt(3, Math.max(0, Math.min(10, research)));
          q.executeUpdate();
        }
        if (shards > 0) insertShard(c, receipt + ":element", id, element, Math.min(10, shards));
        if (!fragment.isEmpty()) insertShard(c, receipt + ":fragment", id, fragment, 1);
        c.commit();
        return true;
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }

  private void insertShard(Connection c, String receipt, UUID id, String element, int amount)
      throws SQLException {
    try (var q =
        c.prepareStatement(
            "INSERT INTO coresuite_magic_shard_rewards(receipt,uuid,element,amount) VALUES"
                + " (?,?,?,?)")) {
      q.setString(1, receipt);
      q.setString(2, id.toString());
      q.setString(3, element);
      q.setInt(4, amount);
      q.executeUpdate();
    }
  }

  public MagicProfile collect(UUID id) throws Exception {
    return collect(id, null, -1);
  }

  public MagicProfile collect(UUID id, String latestPayload, long expectedRevision)
      throws Exception {
    // The shared database executor serializes account writes. A transaction ties claiming to
    // progress.
    try (var c = db.getConnection()) {
      c.setAutoCommit(false);
      try {
        MagicProfile profile;
        try (var q =
            c.prepareStatement(
                "SELECT revision,payload FROM coresuite_magic_profiles WHERE uuid=?")) {
          q.setString(1, id.toString());
          try (var r = q.executeQuery()) {
            if (!r.next()) {
              c.rollback();
              return null;
            }
            profile = MagicProfile.decode(r.getString(2), r.getLong(1));
          }
        }
        if (latestPayload != null) {
          if (profile.revision != expectedRevision)
            throw new SQLException("Magic account changed before reward collection");
          profile = MagicProfile.decode(latestPayload, expectedRevision);
        }
        int amount = 0;
        try (var q =
            c.prepareStatement(
                "SELECT amount FROM coresuite_magic_research WHERE uuid=? AND claimed=0")) {
          q.setString(1, id.toString());
          try (var r = q.executeQuery()) {
            while (r.next()) amount = Math.min(100000, amount + r.getInt(1));
          }
        }
        boolean shardsCollected = collectShards(c, id, profile);
        if (amount > 0 || shardsCollected || latestPayload != null) {
          profile.research = Math.min(100000, profile.research + amount);
          save(c, id, profile.revision, profile.encode());
          profile.revision++;
          try (var q =
              c.prepareStatement(
                  "UPDATE coresuite_magic_research SET claimed=1 WHERE uuid=? AND claimed=0")) {
            q.setString(1, id.toString());
            q.executeUpdate();
          }
        }
        c.commit();
        return profile;
      } catch (Exception e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }

  public void saveWithStock(UUID id, long revision, String payload, String stock, int limit)
      throws SQLException {
    try (var c = db.getConnection()) {
      c.setAutoCommit(false);
      try {
        int used = 0;
        boolean exists;
        try (var q =
            c.prepareStatement("SELECT used FROM coresuite_magic_stock WHERE stock_id=?")) {
          q.setString(1, stock);
          try (var r = q.executeQuery()) {
            exists = r.next();
            if (exists) used = r.getInt(1);
          }
        }
        if (limit < 1 || used >= limit) throw new SQLException("Merchant lesson is sold out");
        if (!exists)
          try (var q =
              c.prepareStatement("INSERT INTO coresuite_magic_stock(stock_id,used) VALUES (?,1)")) {
            q.setString(1, stock);
            q.executeUpdate();
          }
        else
          try (var q =
              c.prepareStatement(
                  "UPDATE coresuite_magic_stock SET used=used+1 WHERE stock_id=? AND used=?")) {
            q.setString(1, stock);
            q.setInt(2, used);
            if (q.executeUpdate() != 1) throw new SQLException("Stock conflict");
          }
        save(c, id, revision, payload);
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }

  public void awardShard(String receipt, UUID id, String element, int amount) throws SQLException {
    try (var c = db.getConnection()) {
      try (var q =
          c.prepareStatement("SELECT receipt FROM coresuite_magic_shard_rewards WHERE receipt=?")) {
        q.setString(1, receipt);
        try (var r = q.executeQuery()) {
          if (r.next()) return;
        }
      }
      try (var q =
          c.prepareStatement(
              "INSERT INTO coresuite_magic_shard_rewards(receipt,uuid,element,amount) VALUES"
                  + " (?,?,?,?)")) {
        q.setString(1, receipt);
        q.setString(2, id.toString());
        q.setString(3, element);
        q.setInt(4, Math.max(1, Math.min(10, amount)));
        q.executeUpdate();
      }
    }
  }

  private boolean collectShards(Connection c, UUID id, MagicProfile profile) throws SQLException {
    boolean found = false;
    try (var q =
        c.prepareStatement(
            "SELECT element,amount FROM coresuite_magic_shard_rewards WHERE uuid=? AND"
                + " claimed=0")) {
      q.setString(1, id.toString());
      try (var r = q.executeQuery()) {
        while (r.next()) {
          found = true;
          String element = r.getString(1);
          if (element.startsWith("boss-")) {
            if (!profile.activeContract.isEmpty()) {
              profile.contractProgress.merge("BOSS", 1, (a, b) -> Math.min(1000, a + b));
              profile.contractProgress.merge(
                  "BOSS:" + element.substring(5), 1, (a, b) -> Math.min(1000, a + b));
            }
          } else profile.shards.merge(element, r.getInt(2), (a, b) -> Math.min(100000, a + b));
        }
      }
    }
    if (found)
      try (var q =
          c.prepareStatement(
              "UPDATE coresuite_magic_shard_rewards SET claimed=1 WHERE uuid=? AND claimed=0")) {
        q.setString(1, id.toString());
        q.executeUpdate();
      }
    return found;
  }

  public void mint(String token, String spell) throws SQLException {
    try (var c = db.getConnection();
        var s = c.prepareStatement("INSERT INTO coresuite_magic_tomes(token,spell) VALUES (?,?)")) {
      s.setString(1, token);
      s.setString(2, spell);
      s.executeUpdate();
    }
  }

  public void redeem(String token, String spell, UUID id, long revision, String payload)
      throws SQLException {
    try (var c = db.getConnection()) {
      c.setAutoCommit(false);
      try {
        try (var s =
            c.prepareStatement(
                "UPDATE coresuite_magic_tomes SET redeemed_by=? WHERE token=? AND spell=? AND"
                    + " redeemed_by IS NULL")) {
          s.setString(1, id.toString());
          s.setString(2, token);
          s.setString(3, spell);
          if (s.executeUpdate() != 1) throw new SQLException("Tome is invalid or already redeemed");
        }
        save(c, id, revision, payload);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }
}
