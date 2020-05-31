def reorder(order):
    . as $in
  | if type == "object" then
        reduce (keys_unsorted|order)[] as $key( {}; . + { ($key):  ($in[$key] | reorder(order)) } )
    elif type == "array" then map( reorder(order) )
    else .
    end;

def licensefirst:
    "license" as $s
  | index($s) as $i
  | if $i==null then . else [$s] + .[:$i] + .[$i+1:] end
;

def versionfirst:
    "version" as $s
  | index($s) as $i
  | if $i==null then . else [$s] + .[:$i] + .[$i+1:] end
;

def replicasfirst:
    "replicas" as $s
  | index($s) as $i
  | if $i==null then . else [$s] + .[:$i] + .[$i+1:] end
;

reorder(replicasfirst) | reorder(licensefirst) | reorder(versionfirst)
