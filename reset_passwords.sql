-- Generate a bcrypt hash and update all class teachers' passwords to Teacher@123
DO $$
DECLARE
  new_hash TEXT;
BEGIN
  -- Generate bcrypt hash for Teacher@123
  new_hash := crypt('Teacher@123', gen_salt('bf', 10));
  
  -- Update all class teachers with this password, reset must_reset_password to false
  UPDATE staff 
  SET password_hash = new_hash, must_reset_password = false
  WHERE school_id = '926c372c-139d-460d-83b1-1a80ef92db57'
    AND role = 'CLASS_TEACHER'
    AND email LIKE '%@vms.school';
  
  RAISE NOTICE 'Password hash used: %', new_hash;
END;
$$;
