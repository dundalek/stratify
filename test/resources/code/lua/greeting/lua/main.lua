local greeting = require("lua/greeting")

local function main()
  print(greeting.greet())
end

main()

-- Run with `luajit lua/main.lua`
