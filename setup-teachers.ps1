param(
  [string]$TOKEN,
  [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57"
)

# Fixed bcrypt hash for password "Teacher@123"
# Generated via pgcrypto: SELECT crypt('Teacher@123', gen_salt('bf',10));
$HASH_CMD = "SELECT crypt('Teacher@123', gen_salt('bf',10));"
$HASH = (docker exec schoolapp-postgres psql -U schoolapp -d schoolapp -t -c $HASH_CMD).Trim()
Write-Host "Hash generated: $($HASH.Substring(0,20))..."

# Reset all pending teachers' passwords
$UPDATE = "UPDATE staff SET password_hash=`'$HASH`', must_reset_password=false WHERE school_id='$TENANT' AND must_reset_password=true RETURNING email;"
$result = docker exec schoolapp-postgres psql -U schoolapp -d schoolapp -c $UPDATE
Write-Host "Updated teachers:"
Write-Host $result

# Now get all section IDs and staff IDs for assignment
$sections = (docker exec schoolapp-postgres psql -U schoolapp -d schoolapp -t -c "SELECT s.id, c.name||'.'||s.name as label FROM sections s JOIN school_classes c ON c.id=s.class_id WHERE s.school_id='$TENANT' AND c.name LIKE 'Class %' ORDER BY c.name, s.name") -split "`n" | Where-Object { $_ -match '\|' }
Write-Host "`nSections:"
$sections | ForEach-Object { Write-Host $_ }

# Get teacher IDs
$teachers = (docker exec schoolapp-postgres psql -U schoolapp -d schoolapp -t -c "SELECT id, first_name||' '||last_name FROM staff WHERE school_id='$TENANT' AND role='CLASS_TEACHER' AND email LIKE '%@vms.school' ORDER BY first_name") -split "`n" | Where-Object { $_ -match '\|' }
Write-Host "`nTeachers:"
$teachers | ForEach-Object { Write-Host $_ }
