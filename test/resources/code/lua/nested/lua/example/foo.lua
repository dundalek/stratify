local bar = require("example.foo.bar")

local M = {}

function M.x()
  return bar.y()
end

return M
